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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.ICacher.SATCacheEntry;
import ca.ubc.cs.beta.stationpacking.cache.ICacher.UNSATCacheEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheUNSATEntry;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;

/**
 * Created by newmanne on 02/12/14.
 * Interfaces with redis to store and retrieve CacheEntry's
 */
@Slf4j
public class RedisCacher {

    private final StringRedisTemplate redisTemplate;

    public RedisCacher(StringRedisTemplate template) {
        this.redisTemplate = template;
    }

    public String cacheResult(CacheCoordinate cacheCoordinate, StationPackingInstance instance, SolverResult result) {
        Preconditions.checkState(result.isConclusive(), "Result must be SAT or UNSAT in order to cache");
        final String jsonResult;
        final Map<String, Object> metadata = instance.getMetadata();
        metadata.put(StationPackingInstance.CACHE_DATE_KEY, new Date());
        if (result.getResult().equals(SATResult.SAT)) {
            final SATCacheEntry entry = new SATCacheEntry(metadata, result.getAssignment());
            jsonResult = JSONUtils.toString(entry);
        } else {
            final UNSATCacheEntry entry = new UNSATCacheEntry(metadata, instance.getDomains());
            jsonResult = JSONUtils.toString(entry);
        }
        final String key = cacheCoordinate.toKey(result.getResult(), instance);
        log.info("Adding result for " + instance.getName() + " to cache with key " + key);
        redisTemplate.boundValueOps(key).set(jsonResult);
        return key;
    }

    public <T> Optional<T> getSolverResultByKey(String key, boolean shouldLog, Class<T> klazz) {
        if (shouldLog) {
            log.info("Asking redis for entry " + key);
        }
        final String value = redisTemplate.boundValueOps(key).get();
        final Optional<T> result;
        if (value != null) {
            final T cacheEntry;
            try {
                cacheEntry = JSONUtils.toObjectWithException(value, klazz);
            } catch (IOException e) {
                throw new RuntimeException("Error parsing key " + key + ". The value at key " + key + " could not be parsed into an object of type " + klazz.getSimpleName() + ". Either fix the malformed JSON in redis (by performing a SET on this key with the corrected JSON), or else simply delete this malformed key", e);
            }
            result = Optional.of(cacheEntry);
        } else {
            result = Optional.empty();
        }
        return result;
    }


    public Optional<SATCacheEntry> getSATSolverResultByKey(String key, boolean shouldLog) {
        return getSolverResultByKey(key, shouldLog, SATCacheEntry.class);
    }

    public Optional<UNSATCacheEntry> getUNSATSolverResultByKey(String key, boolean shouldLog) {
        return getSolverResultByKey(key, shouldLog, UNSATCacheEntry.class);
    }

    public <CONTAINMENT_CACHE_ENTRY, CACHE_ENTRY extends ICacher.ISATFCCacheEntry> ListMultimap<CacheCoordinate, CONTAINMENT_CACHE_ENTRY> processResults(Set<String> keys, Map<CacheCoordinate, ImmutableBiMap<Station, Integer>> coordinateToPermutation, Matcher acceptRegexMatcher, SATResult entryTypeName, Function<String, CACHE_ENTRY> keyToCacheEntry, CacheEntryToContainmentCacheEntryFactory<CACHE_ENTRY, CONTAINMENT_CACHE_ENTRY> cacheEntryToContainmentCacheEntry) {
        final ListMultimap<CacheCoordinate, CONTAINMENT_CACHE_ENTRY> results = ArrayListMultimap.create();
        int i = 0;
        for (String key : keys) {
            i++;
            if (i % 1000 == 0) {
                log.info("Processed {} {} keys out of {}", i, entryTypeName, keys.size());
            }
            final CacheCoordinate coordinate = CacheCoordinate.fromKey(key);
            final ImmutableBiMap<Station, Integer> permutation = coordinateToPermutation.get(coordinate);
            if (permutation == null) {
                log.warn("Skipping cache entry from key {}. Could not find a permutation known for coordinate {}. This probably means that the cache entry does not correspond to any known constraint folders ({})", key, coordinate, coordinateToPermutation.keySet());
                continue;
            }

            final CACHE_ENTRY cacheEntry = keyToCacheEntry.apply(key);

            if (acceptRegexMatcher != null) {
                final String name = (String) cacheEntry.getMetadata().get(StationPackingInstance.NAME_KEY);
                if (name != null && !acceptRegexMatcher.reset(name).matches()) {
                    log.debug("Skipping entry {} because name does not match accept regex", name);
                    continue;
                }
            }

            final CONTAINMENT_CACHE_ENTRY entry = cacheEntryToContainmentCacheEntry.create(cacheEntry, key, permutation);

            results.put(coordinate, entry);
        }

        log.info("Finished processing {} {} entries", i, entryTypeName);
        results.keySet().forEach(cacheCoordinate -> {
            log.info("Found {} {} entries for cache {}", results.get(cacheCoordinate).size(), entryTypeName, cacheCoordinate);
        });
        return results;
    }

    public ContainmentCacheInitData getContainmentCacheInitData(long limit, Map<CacheCoordinate, ImmutableBiMap<Station, Integer>> coordinateToPermutation, String acceptRegex) {
        log.info("Pulling precache data from redis");
        final Watch watch = Watch.constructAutoStartWatch();

        final Set<String> SATKeys = new HashSet<>();
        final Set<String> UNSATKeys = new HashSet<>();

        final Cursor<byte[]> scan = redisTemplate.getConnectionFactory().getConnection().scan(ScanOptions.scanOptions().build());
        long count = 0;
        while (count < limit && scan.hasNext()) {
            final String key = new String(scan.next());
            count++;
            if (key.startsWith("SATFC:SAT:")) {
                SATKeys.add(key);
            } else if (key.startsWith("SATFC:UNSAT:")) {
                UNSATKeys.add(key);
            }
        }

        log.info("Found " + SATKeys.size() + " SAT keys");
        log.info("Found " + UNSATKeys.size() + " UNSAT keys");

        Matcher acceptRegexMatcher = null;
        if (acceptRegex != null) {
            log.info("Only accepting entries matching regex {}", acceptRegex);
            final Pattern acceptRegexPattern = Pattern.compile(acceptRegex);
            acceptRegexMatcher = acceptRegexPattern.matcher("");
        }

        final ListMultimap<CacheCoordinate, ContainmentCacheSATEntry> SATResults = processResults(SATKeys, coordinateToPermutation, acceptRegexMatcher, SATResult.SAT, key -> getSATSolverResultByKey(key, false).get(), new CacheEntryToContainmentCacheEntryFactory<SATCacheEntry, ContainmentCacheSATEntry>() {
            @Override
            public ContainmentCacheSATEntry create(SATCacheEntry thing, String key, ImmutableBiMap<Station, Integer> permutation) {
                return new ContainmentCacheSATEntry(thing.getAssignment(), key, permutation);
            }
        });
        final ListMultimap<CacheCoordinate, ContainmentCacheUNSATEntry> UNSATResults = processResults(UNSATKeys, coordinateToPermutation, acceptRegexMatcher, SATResult.UNSAT, key -> getUNSATSolverResultByKey(key, false).get(), new CacheEntryToContainmentCacheEntryFactory<UNSATCacheEntry, ContainmentCacheUNSATEntry>() {
            @Override
            public ContainmentCacheUNSATEntry create(UNSATCacheEntry thing, String key, ImmutableBiMap<Station, Integer> permutation) {
                return new ContainmentCacheUNSATEntry(thing.getDomains(), key, permutation);
            }
        });

        log.info("It took {}s to pull precache data from redis", watch.getElapsedTime());
        return new ContainmentCacheInitData(SATResults, UNSATResults);
    }

    @Data
    public static class ContainmentCacheInitData {
        private final ListMultimap<CacheCoordinate, ContainmentCacheSATEntry> SATResults;
        private final ListMultimap<CacheCoordinate, ContainmentCacheUNSATEntry> UNSATResults;

        public Set<CacheCoordinate> getCaches() {
            return Sets.union(SATResults.keySet(), UNSATResults.keySet());
        }
    }


    /**
     * Removes the cache entries in Redis
     * @param collection collection of SAT entries
     */
    public void deleteSATCollection(List<ContainmentCacheSATEntry> collection){
        List<String> keys = new ArrayList<>();
        collection.forEach(entry -> keys.add(entry.getKey()));
        redisTemplate.delete(keys);
    }

    /**
     * Removes the cache entries in Redis
     * @param collection collection of UNSAT entries
     */
    public void deleteUNSATCollection(List<ContainmentCacheUNSATEntry> collection){
        List<String> keys = new ArrayList<>();
        collection.forEach(entry -> keys.add(entry.getKey()));
        redisTemplate.delete(keys);
    }

    private interface CacheEntryToContainmentCacheEntryFactory<V, T> {
        T create(V cacheEntry, String key, ImmutableBiMap<Station, Integer> permutation);
    }


}