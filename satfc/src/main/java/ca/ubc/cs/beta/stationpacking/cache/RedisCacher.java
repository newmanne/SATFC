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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.utils.CacheUtils;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.collect.*;
import lombok.Data;
import lombok.Setter;
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
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.BinaryJedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

/**
 * Created by newmanne on 02/12/14.
 * Interfaces with redis to store and retrieve CacheEntry's
 */
@Slf4j
public class RedisCacher {

    private final static int SAT_PIPELINE_SIZE = 10000;
    private final static int UNSAT_PIPELINE_SIZE = 2500;
    public static final String HASH_NUM = "SATFC:HASHNUM";

    private final DataManager dataManager;
    private final StringRedisTemplate redisTemplate;
    private final BinaryJedis binaryJedis;
    private final StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

    public RedisCacher(DataManager dataManager, StringRedisTemplate redisTemplate, BinaryJedis binaryJedis) {
        this.dataManager = dataManager;
        this.redisTemplate = redisTemplate;
        this.binaryJedis = binaryJedis;
    }

    public ISATFCCacheEntry cacheEntryFromKey(String key) {
        return cacheEntryFromKeyAndAnswer(key, binaryJedis.hgetAll(stringRedisSerializer.serialize(key)));
    }

    public ISATFCCacheEntry cacheEntryFromKeyAndAnswer(String key, final Map<byte[], byte[]> answer) {
        final CacheUtils.ParsedKey parsedKey = CacheUtils.parseKey(key);
        final CacheCoordinate coordinate = CacheCoordinate.fromKey(key);
        final ImmutableBiMap<Station, Integer> permutation = dataManager.getData(coordinate).getPermutation();
        final Map<String, byte[]> stringKeyAnswer = answer.entrySet().stream().collect(Collectors.toMap(entry -> stringRedisSerializer.deserialize(entry.getKey()), Map.Entry::getValue));
        if (parsedKey.getResult().equals(SATResult.SAT)) {
            return parseSATEntry(stringKeyAnswer, key, permutation);
        } else {
            return parseUNSATEntry(stringKeyAnswer, key, permutation);
        }
    }

    public static final String ASSIGNMENT_KEY = "assignment";
    public static final String BITSET_KEY = "bitset";
    public static final String NAME_KEY = "name";
    public static final String DOMAINS_KEY = "domains";
    private static final Set<String> SAT_REQUIRED_KEYS = Sets.newHashSet(ASSIGNMENT_KEY, BITSET_KEY);
    private static final Set<String> UNSAT_REQUIRED_KEYS = Sets.newHashSet(DOMAINS_KEY, BITSET_KEY);

    private ContainmentCacheSATEntry parseSATEntry(Map<String, byte[]> entry, String key, ImmutableBiMap<Station, Integer> permutation) {
        if (!entry.keySet().containsAll(SAT_REQUIRED_KEYS)) {
            throw new IllegalArgumentException("Entry does not contain required keys " + SAT_REQUIRED_KEYS + ". Only have keys " + entry.keySet());
        }
        final BitSet bitSet = BitSet.valueOf(entry.get(BITSET_KEY));
        final byte[] channels = entry.get(ASSIGNMENT_KEY);
        final String name = stringRedisSerializer.deserialize(entry.get(NAME_KEY));
        final String auction = StationPackingUtils.parseAuctionFromName(name);
        return new ContainmentCacheSATEntry(bitSet, channels, key, permutation, auction);
    }

    private ContainmentCacheUNSATEntry parseUNSATEntry(Map<String, byte[]> entry, String key, ImmutableBiMap<Station, Integer> permutation) {
        if (!entry.keySet().containsAll(UNSAT_REQUIRED_KEYS)) {
            throw new IllegalArgumentException("Entry does not contain required keys " + UNSAT_REQUIRED_KEYS + ". Only have keys " + entry.keySet());
        }
        final BitSet bitSet = BitSet.valueOf(entry.get(BITSET_KEY));
        final BitSet domains = BitSet.valueOf(entry.get(DOMAINS_KEY));
        final String name = stringRedisSerializer.deserialize(entry.get(NAME_KEY));
        final String auction = StationPackingUtils.parseAuctionFromName(name);
        return new ContainmentCacheUNSATEntry(bitSet, domains, key, permutation, auction);
    }

    public <T extends ISATFCCacheEntry> String cacheResult(CacheCoordinate coordinate, T entry, String name) {
        final long newID = redisTemplate.boundValueOps(HASH_NUM).increment(1);
        final String key = coordinate.toKey(SATResult.SAT, newID);
        final byte[] keyBytes = stringRedisSerializer.serialize(key);
        final Transaction multi = binaryJedis.multi();
            multi.hset(keyBytes, stringRedisSerializer.serialize(BITSET_KEY), entry.getBitSet().toByteArray());
            if (entry instanceof ContainmentCacheSATEntry) {
                multi.hset(keyBytes, stringRedisSerializer.serialize(ASSIGNMENT_KEY), ((ContainmentCacheSATEntry) entry).getChannels());
            } else if (entry instanceof ContainmentCacheUNSATEntry) {
                multi.hset(keyBytes, stringRedisSerializer.serialize(DOMAINS_KEY), ((ContainmentCacheUNSATEntry) entry).getDomainsBitSet().toByteArray());
            }
            if (name != null) {
                multi.hset(keyBytes, stringRedisSerializer.serialize(NAME_KEY), stringRedisSerializer.serialize(name));
            }
        multi.exec();
        if (name != null) {
            log.info("Adding result for {} to cache with key {}", name, key);
        }
        return key;
    }

    public <CONTAINMENT_CACHE_ENTRY extends ISATFCCacheEntry> ListMultimap<CacheCoordinate, CONTAINMENT_CACHE_ENTRY> processResults(Set<String> keys, SATResult entryTypeName, int partitionSize) {
        final ListMultimap<CacheCoordinate, CONTAINMENT_CACHE_ENTRY> results = ArrayListMultimap.create();
        final AtomicInteger numProcessed = new AtomicInteger();
        Lists.partition(new ArrayList<>(keys), partitionSize).stream().forEach(keyChunk -> {
            log.info("Processed {} {} keys out of {}", numProcessed, entryTypeName, keys.size());
            final List<String> orderedKeys = new ArrayList<>();
            final List<Response<Map<byte[], byte[]>>> responses = new ArrayList<>();
            final Pipeline p = binaryJedis.pipelined();
            for (String key : keyChunk) {
                numProcessed.incrementAndGet();
                final CacheCoordinate coordinate = CacheCoordinate.fromKey(key);
                final ImmutableBiMap<Station, Integer> permutation = dataManager.getData(coordinate).getPermutation();
                if (permutation == null) {
                    log.warn("Skipping cache entry from key {}. Could not find a permutation known for coordinate {}. This probably means that the cache entry does not correspond to any known constraint folders ({})", key, coordinate, dataManager.getCoordinateToBundle().keySet());
                    continue;
                }
                orderedKeys.add(key);
                responses.add(p.hgetAll(stringRedisSerializer.serialize(key)));
            }
            p.sync();
            Preconditions.checkState(responses.size() == orderedKeys.size(), "Different number of queries and answers from redis!");
            for (int i = 0; i < responses.size(); i++) {
                final String key = orderedKeys.get(i);
                final CacheCoordinate coordinate = CacheCoordinate.fromKey(key);
                final Map<byte[], byte[]> answer = responses.get(i).get();
                try {
                    final ISATFCCacheEntry cacheEntry = cacheEntryFromKeyAndAnswer(key, answer);
                    results.put(coordinate, (CONTAINMENT_CACHE_ENTRY) cacheEntry);
                } catch (Exception e) {
                    log.error("Error making cache entry for key {}", key, e);
                }

            }
        });
        log.info("Finished processing {} {} entries", numProcessed, entryTypeName);
        results.keySet().forEach(cacheCoordinate -> {
            log.info("Found {} {} entries for cache {}", results.get(cacheCoordinate).size(), entryTypeName, cacheCoordinate);
        });
        return results;
    }

    public ContainmentCacheInitData getContainmentCacheInitData(long limit, boolean skipSAT, boolean skipUNSAT) {
        log.info("Pulling precache data from redis");
        final Watch watch = Watch.constructAutoStartWatch();

        final Set<String> SATKeys = new HashSet<>();
        final Set<String> UNSATKeys = new HashSet<>();

        final Cursor<byte[]> scan = redisTemplate.getConnectionFactory().getConnection().scan(ScanOptions.scanOptions().build());
        while (SATKeys.size() + UNSATKeys.size() < limit && scan.hasNext()) {
            final String key = new String(scan.next());
            if (key.equals(HASH_NUM)) {
                continue;
            }
            final CacheUtils.ParsedKey parsedKey = CacheUtils.parseKey(key);
            if (parsedKey.getResult().equals(SATResult.SAT) && !skipSAT) {
                SATKeys.add(key);
            } else if (parsedKey.getResult().equals(SATResult.UNSAT) && !skipUNSAT) {
                UNSATKeys.add(key);
            }
        }

        // filter out coordinates we don't know about
        SATKeys.removeIf(key -> !dataManager.getCoordinateToBundle().containsKey(CacheCoordinate.fromKey(key)));
        UNSATKeys.removeIf(key -> !dataManager.getCoordinateToBundle().containsKey(CacheCoordinate.fromKey(key)));

        log.info("Found " + SATKeys.size() + " SAT keys");
        log.info("Found " + UNSATKeys.size() + " UNSAT keys");

        final ListMultimap<CacheCoordinate, ContainmentCacheSATEntry> SATResults = processResults(SATKeys, SATResult.SAT, SAT_PIPELINE_SIZE);
        final ListMultimap<CacheCoordinate, ContainmentCacheUNSATEntry> UNSATResults = processResults(UNSATKeys, SATResult.UNSAT, UNSAT_PIPELINE_SIZE);

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
    public void deleteSATCollection(List<ContainmentCacheSATEntry> collection) {
        redisTemplate.delete(collection.stream().map(ContainmentCacheSATEntry::getKey).collect(Collectors.toList()));
    }

    /**
     * Removes the cache entries in Redis
     * @param collection collection of UNSAT entries
     */
    public void deleteUNSATCollection(List<ContainmentCacheUNSATEntry> collection){
        redisTemplate.delete(collection.stream().map(ContainmentCacheUNSATEntry::getKey).collect(Collectors.toList()));
    }

    public Iterable<ContainmentCacheSATEntry> iterateSAT() {
        final Cursor<byte[]> scan = redisTemplate.getConnectionFactory().getConnection().scan(ScanOptions.scanOptions().build());
        return () -> new AbstractIterator<ContainmentCacheSATEntry>() {

            @Override
            protected ContainmentCacheSATEntry computeNext() {
                while (scan.hasNext()) {
                    final String key = new String(scan.next());
                    final CacheUtils.ParsedKey parsedKey = CacheUtils.parseKey(key);
                    if (parsedKey.getResult().equals(SATResult.SAT)) {
                        return (ContainmentCacheSATEntry) cacheEntryFromKey(key);
                    }
                }
                return endOfData();
            }
        };
    }

}