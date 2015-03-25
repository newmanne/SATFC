/**
 * Copyright 2015, Auctionomics, Alexandre Fréchette, Kevin Leyton-Brown.
 *
 * This file is part of satfcserver.
 *
 * satfcserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * satfcserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with satfcserver.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.webapp.rest;

import ca.ubc.cs.beta.stationpacking.cache.CacherProxy.ContainmentCacheCacheRequest;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache;
import ca.ubc.cs.beta.stationpacking.cache.ICacheLocator;
import ca.ubc.cs.beta.stationpacking.cache.RedisCacher;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy.ContainmentCacheRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
@Slf4j
@RequestMapping("/v1/cache")
public class ContainmentCacheController extends AbstractController {

    @Autowired
    ICacheLocator containmentCache;

    @Autowired
    RedisCacher cacher;

    // note that while this is conceptually a GET request, the fact that we need to send json means that its simpler to achieve as a POST
    @RequestMapping(value = "/query/SAT", method = RequestMethod.POST, produces = JSON_CONTENT)
    @ResponseBody
    public ContainmentCache.ContainmentCacheSATResult lookupSAT(
            @RequestBody final ContainmentCacheRequest instance
    ) {
        final Optional<ContainmentCache> cache = containmentCache.locate(instance.getCoordinate());
        if (cache.isPresent()) {
            return cache.get().proveSATBySuperset(instance.getInstance());
        } else {
            return new ContainmentCache.ContainmentCacheSATResult();
        }
    }

    // note that while this is conceptually a GET request, the fact that we need to send json means that its simpler to achieve as a POST
    @RequestMapping(value = "/query/UNSAT", method = RequestMethod.POST, produces = JSON_CONTENT)
    @ResponseBody
    public ContainmentCache.ContainmentCacheUNSATResult lookupUNSAT(
            @RequestBody final ContainmentCacheRequest instance
    ) {
        final Optional<ContainmentCache> cache = containmentCache.locate(instance.getCoordinate());
        if (cache.isPresent()) {
            return cache.get().proveUNSATBySubset(instance.getInstance());
        } else {
            return new ContainmentCache.ContainmentCacheUNSATResult();
        }
    }

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public void cache(
            @RequestBody final ContainmentCacheCacheRequest request
    ) {
        cacher.cacheResult(request.getCoordinate(), request.getInstance(), request.getResult());
    }

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public String temp() {
        return "H";
    }

}