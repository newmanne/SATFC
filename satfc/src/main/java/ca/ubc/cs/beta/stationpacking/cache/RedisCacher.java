package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheUNSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.ICacher.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.cache.ICacher.SATCacheEntry;
import ca.ubc.cs.beta.stationpacking.cache.ICacher.UNSATCacheEntry;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.*;

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

    public void cacheResult(CacheCoordinate cacheCoordinate, StationPackingInstance instance, SolverResult result) {
        final String jsonResult;
        final Map<String, Object> metadata = instance.getMetadata();
        metadata.put(StationPackingInstance.CACHE_DATE_KEY, new Date());
        if (result.getResult().equals(SATResult.SAT)) {
            final SATCacheEntry entry = new SATCacheEntry(metadata, result.getAssignment());
            jsonResult = JSONUtils.toString(entry);
        } else {
            Preconditions.checkState(result.getResult().equals(SATResult.UNSAT));
            final UNSATCacheEntry entry = new UNSATCacheEntry(metadata, instance.getDomains());
            jsonResult = JSONUtils.toString(entry);
        }
        final String key = cacheCoordinate.toKey(SATResult.SAT, instance);
        log.info("Adding result for " + instance.getName() + " to cache with key " + key);
        redisTemplate.boundValueOps(key).set(jsonResult);
    }


    public Optional<SATCacheEntry> getSATSolverResultByKey(String key, boolean shouldLog) {
        if (shouldLog) {
            log.info("Asking redis for entry " + key);
        }
        final String value = redisTemplate.boundValueOps(key).get();
        final Optional<SATCacheEntry> result;
        if (value != null) {
            final SATCacheEntry cacheEntry = JSONUtils.toObject(value, SATCacheEntry.class);
            result = Optional.of(cacheEntry);
        } else {
            result = Optional.empty();
        }
        return result;
    }

    public Optional<UNSATCacheEntry> getUNSATSolverResultByKey(String key, boolean shouldLog) {
        if (shouldLog) {
            log.info("Asking redis for entry " + key);
        }
        final String value = redisTemplate.boundValueOps(key).get();
        final Optional<UNSATCacheEntry> result;
        if (value != null) {
            final UNSATCacheEntry cacheEntry = JSONUtils.toObject(value, UNSATCacheEntry.class);
            result = Optional.of(cacheEntry);
        } else {
            result = Optional.empty();
        }
        return result;

    }

    public ContainmentCacheInitData getContainmentCacheInitData() {
        log.info("Pulling precache data from redis");
        long start = System.currentTimeMillis();

        final ListMultimap<CacheCoordinate, ContainmentCacheSATEntry> SATResults = ArrayListMultimap.create();
        final ListMultimap<CacheCoordinate, ContainmentCacheUNSATEntry> UNSATResults = ArrayListMultimap.create();

        final Set<String> SATKeys = redisTemplate.keys("SATFC:SAT:*");
        log.info("Found " + SATKeys.size() + " SAT keys");

        // process SATs
        final AtomicInteger progressIndex = new AtomicInteger();
        SATKeys.forEach(key -> {
            if (progressIndex.get() % 1000 == 0) {
                log.info("Processed " + progressIndex.get() + " SAT keys out of " + SATKeys.size());
            }
            final CacheCoordinate coordinate = CacheCoordinate.fromKey(key);
            final SATCacheEntry cacheEntry = getSATSolverResultByKey(key, false).get();
            final ContainmentCacheSATEntry entry = new ContainmentCacheSATEntry(cacheEntry.getAssignment(), key);
            SATResults.put(coordinate, entry);
            progressIndex.incrementAndGet();
        });
        log.info("Finished processing SAT entries");
        SATResults.keySet().forEach(cacheCoordinate -> {
            log.info("Found {} SAT entries for cache " + cacheCoordinate, SATResults.get(cacheCoordinate).size());
        });

        // process UNSATs
        final Set<String> UNSATKeys = redisTemplate.keys("SATFC:UNSAT:*");
        log.info("Found " + UNSATKeys.size() + " UNSAT keys");

        progressIndex.set(0);
        UNSATKeys.forEach(key -> {
            if (progressIndex.get() % 1000 == 0) {
                log.info("Processed " + progressIndex.get() + " UNSAT keys out of " + UNSATKeys.size());
            }
            final CacheCoordinate coordinate = CacheCoordinate.fromKey(key);
            final UNSATCacheEntry cacheEntry = getUNSATSolverResultByKey(key, false).get();
            final ContainmentCacheUNSATEntry entry = new ContainmentCacheUNSATEntry(cacheEntry.getDomains(), key);
            UNSATResults.put(coordinate, entry);
            progressIndex.incrementAndGet();
        });
        log.info("Finished processing UNSAT entries");
        UNSATResults.keySet().forEach(cacheCoordinate -> {
            log.info("Found {} UNSAT entries for cache " + cacheCoordinate, UNSATResults.get(cacheCoordinate).size());
        });

        log.info("It took {}s to pull precache data from redis", (System.currentTimeMillis() - start) / 1000.0);

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

}