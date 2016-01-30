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

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheUNSATEntry;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Created by newmanne on 02/12/14.
 * Interfaces with redis to store and retrieve CacheEntry's
 */
@Slf4j
public class RedisCacher {

    private final static int SAT_PIPELINE_SIZE = 10000;
    private final static int UNSAT_PIPELINE_SIZE = 2500;
    public static final String HASH_NUM = "SATFC:HASHNUM";

    private final StringRedisTemplate redisTemplate;

    public RedisCacher(StringRedisTemplate template) {
        this.redisTemplate = template;
    }

    private interface RedisCacheEntryToContainmentCacheEntryConverter<T> {
        T convert(Map<String, String> redisCacheEntry, String key, ImmutableBiMap<Station, Integer> permutation);
    }

    public static final String ASSIGNMENT_KEY = "assignment";
    public static final String BITSET_KEY = "bitset";
    public static final String NAME_KEY = "name";
    public static final String DOMAINS_KEY = "domains";
    private static final Set<String> SAT_REQUIRED_KEYS = Sets.newHashSet(ASSIGNMENT_KEY, BITSET_KEY);

    private final static RedisCacheEntryToContainmentCacheEntryConverter<ContainmentCacheSATEntry> SATCacheConverter = (entry, key, permutation) -> {
        if (!entry.keySet().containsAll(SAT_REQUIRED_KEYS)) {
            throw new IllegalArgumentException("Entry does not contain required keys " + SAT_REQUIRED_KEYS + ". Only have keys " + entry.keySet());
        }
        final BitSet bitSet = BitSet.valueOf(entry.get(BITSET_KEY).getBytes());
        final byte[] channels = entry.get(ASSIGNMENT_KEY).getBytes();
        return new ContainmentCacheSATEntry(bitSet, channels, key, permutation, entry.get(NAME_KEY));
    };

    public String cacheResult(CacheCoordinate cacheCoordinate, StationPackingInstance instance, SolverResult result) {
        Preconditions.checkState(result.isConclusive(), "Result must be SAT or UNSAT in order to cache");
        final String key;
        final Map<String, String> hash = new HashMap<>();
        hash.put(BITSET_KEY, null);
        final Long newID = redisTemplate.boundValueOps(HASH_NUM).increment(1);
        if (instance.hasName()) {
            hash.put(NAME_KEY, instance.getName());
        }
        if (result.getResult().equals(SATResult.SAT)) {
            hash.put(ASSIGNMENT_KEY, null);
        } else {
            hash.put(DOMAINS_KEY, null);
        }
        key = cacheCoordinate.toKey(result.getResult(), newID);
        redisTemplate.opsForHash().putAll(key, hash);
        log.info("Adding result for " + instance.getName() + " to cache with key " + key);
        return key;
    }


    private final static RedisCacheEntryToContainmentCacheEntryConverter<ContainmentCacheUNSATEntry> UNSATCacheConverter = (entry, key, permutation) -> null;

    public <CONTAINMENT_CACHE_ENTRY extends ISATFCCacheEntry> ListMultimap<CacheCoordinate, CONTAINMENT_CACHE_ENTRY> processResults(Set<String> keys, Map<CacheCoordinate, ImmutableBiMap<Station, Integer>> coordinateToPermutation, SATResult entryTypeName, RedisCacheEntryToContainmentCacheEntryConverter<CONTAINMENT_CACHE_ENTRY> redisCacheEntryToContainmentCacheEntryConverter, int partitionSize) {
        final ListMultimap<CacheCoordinate, CONTAINMENT_CACHE_ENTRY> results = ArrayListMultimap.create();
        final AtomicInteger numProcessed = new AtomicInteger();
        Lists.partition(new ArrayList<>(keys), partitionSize).stream().forEach(keyChunk -> {
            log.info("Processed {} {} keys out of {}", numProcessed, entryTypeName, keys.size());
            final List<String> orderedKeys = new ArrayList<>();
            final List<Object> redisAnswers = redisTemplate.executePipelined(new RedisCallback<Object>() {
                @Override
                public Object doInRedis(RedisConnection connection) throws DataAccessException {
                    final StringRedisConnection stringRedisConn = (StringRedisConnection) connection;
                    for (String key : keyChunk) {
                        numProcessed.incrementAndGet();
                        final CacheCoordinate coordinate = CacheCoordinate.fromKey(key);
                        final ImmutableBiMap<Station, Integer> permutation = coordinateToPermutation.get(coordinate);
                        if (permutation == null) {
                            log.warn("Skipping cache entry from key {}. Could not find a permutation known for coordinate {}. This probably means that the cache entry does not correspond to any known constraint folders ({})", key, coordinate, coordinateToPermutation.keySet());
                            continue;
                        }
                        orderedKeys.add(key);
                        stringRedisConn.hGetAll(key);
                    }
                    // Must return null, actual list returned will be populated
                    return null;
                }
            });
            Preconditions.checkState(redisAnswers.size() == orderedKeys.size(), "Different number of queries and answers from redis!");
            for (int i = 0; i < redisAnswers.size(); i++) {
                final String key = orderedKeys.get(i);
                final CacheCoordinate coordinate = CacheCoordinate.fromKey(key);
                final ImmutableBiMap<Station, Integer> permutation = coordinateToPermutation.get(coordinate);
                final Map<String, String> answer = (Map<String, String>) redisAnswers.get(i);
                final CONTAINMENT_CACHE_ENTRY cacheEntry = redisCacheEntryToContainmentCacheEntryConverter.convert(answer, key, permutation);
                results.put(coordinate, cacheEntry);
            }
        });
        log.info("Finished processing {} {} entries", numProcessed, entryTypeName);
        results.keySet().forEach(cacheCoordinate -> {
            log.info("Found {} {} entries for cache {}", results.get(cacheCoordinate).size(), entryTypeName, cacheCoordinate);
        });
        return results;
    }

    public ContainmentCacheInitData getContainmentCacheInitData(long limit, Map<CacheCoordinate, ImmutableBiMap<Station, Integer>> coordinateToPermutation, String acceptRegex, boolean skipSAT, boolean skipUNSAT) {
        log.info("Pulling precache data from redis");
        final Watch watch = Watch.constructAutoStartWatch();

        final Set<String> SATKeys = new HashSet<>();
        final Set<String> UNSATKeys = new HashSet<>();

        final Cursor<byte[]> scan = redisTemplate.getConnectionFactory().getConnection().scan(ScanOptions.scanOptions().build());
        while (SATKeys.size() + UNSATKeys.size() < limit && scan.hasNext()) {
            final String key = new String(scan.next());
            if (key.startsWith("SATFC:SAT:") && !skipSAT) {
                SATKeys.add(key);
            } else if (key.startsWith("SATFC:UNSAT:") && !skipUNSAT) {
                UNSATKeys.add(key);
            }
        }

        // filter out coordinates we don't know about
        SATKeys.removeIf(key -> !coordinateToPermutation.containsKey(CacheCoordinate.fromKey(key)));
        UNSATKeys.removeIf(key -> !coordinateToPermutation.containsKey(CacheCoordinate.fromKey(key)));

        log.info("Found " + SATKeys.size() + " SAT keys");
        log.info("Found " + UNSATKeys.size() + " UNSAT keys");

        final ListMultimap<CacheCoordinate, ContainmentCacheSATEntry> SATResults = processResults(SATKeys, coordinateToPermutation, SATResult.SAT, SATCacheConverter, SAT_PIPELINE_SIZE);
        final ListMultimap<CacheCoordinate, ContainmentCacheUNSATEntry> UNSATResults = processResults(UNSATKeys, coordinateToPermutation, SATResult.UNSAT, UNSATCacheConverter, UNSAT_PIPELINE_SIZE);

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

}