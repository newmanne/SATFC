package ca.ubc.cs.beta.stationpacking.cache;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheUNSATEntry;
import com.google.common.base.Preconditions;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheEntry;
import ca.ubc.cs.beta.stationpacking.cache.ICacher.IContainmentCacher;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;

/**
 * Created by newmanne on 02/12/14.
 * Interfaces with redis to store and retrieve CacheEntry's
 */
@Slf4j
@RequiredArgsConstructor
public class RedisCacher implements IContainmentCacher {

    private final Jedis fJedis;

    @Override
    public void cacheResult(CacheCoordinate cacheCoordinate, SATCacheEntry entry) {
        final String jsonResult = JSONUtils.toString(entry);
        final String key = cacheCoordinate.toKey();
        fJedis.set(key, jsonResult);
        log.info("Adding result for " + entry.getName() + " to cache with key " + key);
    }

    public Optional<SATCacheEntry> getSolverResultByKey(String key, boolean shouldLog) {
        if (shouldLog) {
            log.info("Asking redis for entry " + key);
        }
        final String value = fJedis.get(key);
        final Optional<SATCacheEntry> result;
        if (value != null) {
            final SATCacheEntry cacheEntry = JSONUtils.toObject(value, SATCacheEntry.class);
            result = Optional.of(cacheEntry);
        } else {
            result = Optional.empty();
        }
        return result;
    }

    @Override
    public Optional<SATCacheEntry> getSolverResultByKey(CacheCoordinate coordinate) {
        return getSolverResultByKey(coordinate.toKey(), true);
    }

    @Override
    public SubsetCacheInitData getSubsetCacheData() {
        log.info("Pulling precache data from redis");
        long start = System.currentTimeMillis();
        List<ContainmentCacheSATEntry> SATResults = new ArrayList<>();
        List<ContainmentCacheUNSATEntry> UNSATResults = new ArrayList<>();
        final Set<String> SATKeys = fJedis.keys("SATFC:SAT:*");
        SATKeys.forEach(key -> {
            final SATCacheEntry cacheEntry = getSolverResultByKey(key, false).get();
            Preconditions.checkState(cacheEntry.getSolverResult().getResult().equals(SATResult.SAT));
            SATResults.add(new ContainmentCacheSATEntry(cacheEntry.getSolverResult().getAssignment()));
        });
        final Set<String> UNSATKeys = fJedis.keys("SATFC:UNSAT:*");
        UNSATKeys.forEach(key -> {
            final SATCacheEntry cacheEntry = getSolverResultByKey(key, false).get();
            Preconditions.checkState(cacheEntry.getSolverResult().getResult().equals(SATResult.UNSAT));
            UNSATResults.add(new ContainmentCacheUNSATEntry(cacheEntry.getSolverResult().getAssignment()));
        });
        log.info("It took {}s to pull precache data from redis. Found {} applicable results. {} SAT and {} UNSAT", (System.currentTimeMillis() - start) / 1000.0, SATResults.size() + UNSATResults.size(), SATResults.size(), UNSATResults.size());
        return new SubsetCacheInitData(SATResults, UNSATResults);
    }

    private BitSet keyToBitSet(String key) {
        return BitSet.valueOf(new CacheCoordinate(key).getProblem().getBytes());
    }

    @Data
    public static class SubsetCacheInitData {
        private final List<ContainmentCacheSATEntry> SATResults;
        private final List<ContainmentCacheUNSATEntry> UNSATResults;
    }

}