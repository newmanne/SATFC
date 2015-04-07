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
package ca.ubc.cs.beta.stationpacking.cache;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import ca.ubc.cs.beta.stationpacking.cache.ICacher.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.cache.RedisCacher.ContainmentCacheInitData;

/**
 * Created by newmanne on 25/03/15.
 */
@Slf4j
public class CacheLocator implements ICacheLocator, ApplicationListener<ContextRefreshedEvent> {

    private final RedisCacher cacher;
    private final ConcurrentMap<CacheCoordinate, ContainmentCache> caches;

    public CacheLocator(RedisCacher cacher) {
        this.cacher = cacher;
        caches = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<ContainmentCache> locate(CacheCoordinate coordinate) {
        return Optional.ofNullable(caches.get(coordinate));
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.info("Beginning to init caches");
        final ContainmentCacheInitData containmentCacheInitData = cacher.getContainmentCacheInitData();
        containmentCacheInitData.getCaches().forEach(cacheCoordinate -> {
            caches.put(cacheCoordinate, new ContainmentCache(containmentCacheInitData.getSATResults().get(cacheCoordinate), containmentCacheInitData.getUNSATResults().get(cacheCoordinate)));
        });

    }
}
