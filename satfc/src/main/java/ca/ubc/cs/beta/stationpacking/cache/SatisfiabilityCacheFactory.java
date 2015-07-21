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

import java.util.Collection;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheUNSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.SatisfiabilityCache;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ISatisfiabilityCache;

import com.google.common.collect.ImmutableBiMap;

import containmentcache.ILockableContainmentCache;
import containmentcache.bitset.simple.SimpleBitSetCache;
import containmentcache.decorators.BufferedThreadSafeCacheDecorator;

/**
* Created by newmanne on 22/04/15.
*/
@Slf4j
public class SatisfiabilityCacheFactory implements ISatisfiabilityCacheFactory {

    private static final int SAT_BUFFER_SIZE = 100;
    private static final int UNSAT_BUFFER_SIZE = 3;

    @Override
    public ISatisfiabilityCache create(Collection<ContainmentCacheSATEntry> SATEntries, Collection<ContainmentCacheUNSATEntry> UNSATEntries, ImmutableBiMap<Station, Integer> permutation) {
        // 1) Create other permutations, if any

        // 2) Create the actual caches and add all the entries
        final ILockableContainmentCache<Station, ContainmentCacheSATEntry> SATCache = BufferedThreadSafeCacheDecorator.makeBufferedThreadSafe(new SimpleBitSetCache<>(permutation), SAT_BUFFER_SIZE);
        log.info("Adding " + SATEntries.size() + " entries to the SAT cache");
        SATCache.addAll(SATEntries);
        final ILockableContainmentCache<Station, ContainmentCacheUNSATEntry> UNSATCache = BufferedThreadSafeCacheDecorator.makeBufferedThreadSafe(new SimpleBitSetCache<>(permutation), UNSAT_BUFFER_SIZE);
        log.info("Adding " + UNSATEntries.size() + " entries to the UNSAT cache");
        UNSATCache.addAll(UNSATEntries);
        return new SatisfiabilityCache(permutation, SATCache, UNSATCache);
    }
}