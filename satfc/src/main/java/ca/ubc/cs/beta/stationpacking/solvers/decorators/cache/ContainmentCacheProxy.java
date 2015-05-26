/**
 * Copyright 2015, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 *
 * This file is part of SATFC.
 *
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.web.util.UriComponentsBuilder;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.ICacher.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATResult;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheUNSATResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;

/**
 * Created by newmanne on 01/03/15.
 * Abstracts away the Containment Cache data structure, which is really being accessed using web requests
 * Not threadsafe!
 */
@Slf4j
public class ContainmentCacheProxy {

    private final CacheCoordinate coordinate;
    private final CloseableHttpClient httpClient;
    private final String SAT_URL;
    private final String UNSAT_URL;

    private final Lock lock;
    private final AtomicBoolean activeSolve;
    private HttpPost post;

    public ContainmentCacheProxy(String baseServerURL, CacheCoordinate coordinate) {
        SAT_URL = baseServerURL + "/v1/cache/query/SAT";
        UNSAT_URL = baseServerURL + "/v1/cache/query/UNSAT";
        this.coordinate = coordinate;
        httpClient = HttpClients.createDefault();
        lock = new ReentrantLock();
        activeSolve = new AtomicBoolean(false);
    }

    /**
     * Object used to represent a cache lookup request
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ContainmentCacheRequest {
        private StationPackingInstance instance;
        private CacheCoordinate coordinate;
    }

    public ContainmentCacheSATResult proveSATBySuperset(StationPackingInstance instance, ITerminationCriterion terminationCriterion) {
        return makePost(SAT_URL, instance, ContainmentCacheSATResult.class, ContainmentCacheSATResult.failure(), terminationCriterion);
    }

    public ContainmentCacheUNSATResult proveUNSATBySubset(StationPackingInstance instance, ITerminationCriterion terminationCriterion) {
        return makePost(UNSAT_URL, instance, ContainmentCacheUNSATResult.class, ContainmentCacheUNSATResult.failure(), terminationCriterion);
    }

    private <T> T makePost(String URL, StationPackingInstance instance, Class<T> responseClass, T failure, ITerminationCriterion terminationCriterion) {
        final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(URL);
        final String uriString = builder.build().toUriString();
        post = new HttpPost(uriString);
        log.debug("Making a request to the cache server for instance " + instance.getName() + " " + uriString);
        final ContainmentCacheRequest request = new ContainmentCacheRequest(instance, coordinate);
        final String jsonRequest = JSONUtils.toString(request);
        final StringEntity stringEntity = new StringEntity(jsonRequest, ContentType.APPLICATION_JSON);
        post.setEntity(stringEntity);
        try {
            lock.lock();
            activeSolve.set(true);
            lock.unlock();
            if (terminationCriterion.hasToStop()) {
                return failure;
            }
            final CloseableHttpResponse httpResponse = httpClient.execute(post);
            final String response = EntityUtils.toString(httpResponse.getEntity());
            return JSONUtils.toObject(response, responseClass);
        } catch (IOException e) {
            if (post.isAborted()) {
                log.trace("Web request was aborted");
                return failure;
            } else {
                throw new RuntimeException("Could not contact server", e);
            }
        } finally {
            lock.lock();
            activeSolve.set(false);
            lock.unlock();
        }
    }

    public void interrupt() {
        lock.lock();
        if (activeSolve.get()) {
            post.abort();
        }
        lock.unlock();
    }

}
