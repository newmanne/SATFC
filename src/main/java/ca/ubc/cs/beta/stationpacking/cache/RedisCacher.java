package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.SupersetSubsetCache.PrecacheSupersetEntry;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;

import com.google.common.hash.HashCode;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import java.util.*;

/**
 * Created by newmanne on 02/12/14.
 * Interfaces with redis to store and retrieve CacheEntry's
 */
@Slf4j
@RequiredArgsConstructor
public class RedisCacher implements ICacher {

    private final Jedis fJedis;
    private final StationPackingInstanceHasher fHasher;
    private final String INTERFERENCE_NAME = "021814SC3M";
    
    @Override
    public void cacheResult(StationPackingInstance instance, CacheEntry entry) {
        final String jsonResult = JSONUtils.toString(entry);
        final String key = getKey(fHasher.hash(instance));
        fJedis.set(key, jsonResult);
        log.info("Adding result " + instance.getName() + " to cache with key " + key);
    }

    @Override
    public Optional<CacheEntry> getSolverResultFromCache(StationPackingInstance aInstance) {
    	HashCode hash = fHasher.hash(aInstance);
    	Optional<CacheEntry> cachedResult = getSolverResultFromCache(hash);
        while (cachedResult.isPresent() && !cachedResult.get().getDomains().equals(aInstance.getDomains())) {
            log.debug("Hash " + hash + " has a collision, rehashing");
            hash = fHasher.rehash(hash);
            cachedResult = getSolverResultFromCache(hash);
        }
        return cachedResult;
    }

    private Optional<CacheEntry> getSolverResultFromCache(HashCode hash) {
        final String key = getKey(hash);
        return getSolverResultByKey(key);
    }

    private String getKey(HashCode hash) {
        return "SATFC:" + INTERFERENCE_NAME + ":" + hash;
    }

	@Override
	public Optional<CacheEntry> getSolverResultByKey(String key) {
        return getSolverResultByKey(key, true);
    }



    public Optional<CacheEntry> getSolverResultByKey(String key, boolean shouldLog) {
        if (shouldLog) {
            log.info("Asking redis for entry " + key);
        }
        final String value = fJedis.get(key);
        final Optional<CacheEntry> result;
        if (value != null) {
            final CacheEntry cacheEntry = JSONUtils.toObject(value, CacheEntry.class);
            result = Optional.of(cacheEntry);
        } else {
            result = Optional.empty();
        }
        return result;	
    }

    @Override
	public SubsetCacheInitData getSubsetCacheData() {
        log.info("Pulling precache data from redis");
        long start = System.currentTimeMillis();
		List<PrecacheSupersetEntry> SATResults = new ArrayList<>();
		List<BitSet> UNSATResults = new ArrayList<>();
        // TODO: can prefix keys with UHF or something to speed this up
		final Set<String> keys = fJedis.keys("*");
		keys.forEach(key -> {
			CacheEntry cacheEntry = getSolverResultByKey(key, false).get();
			boolean UHFProblem = cacheEntry.getDomains().entrySet().stream().allMatch(entry -> StationPackingUtils.UHF_CHANNELS.containsAll(entry.getValue()));
			if (UHFProblem) {
                final SATResult result = cacheEntry.getSolverResult().getResult();
                if (result.equals(SATResult.SAT)) {
                    SATResults.add(new PrecacheSupersetEntry(key, new StationPackingInstance(cacheEntry.getDomains(), StationPackingInstance.UNTITLED).toBitSet()));
                } else if (result.equals(SATResult.UNSAT)) {
                    UNSATResults.add(new StationPackingInstance(cacheEntry.getDomains(), StationPackingInstance.UNTITLED).toBitSet());
                }
            }
        });
        log.info("It took {}s to pull precache data from redis. Found {} applicable results. {} SAT and {} UNSAT", (System.currentTimeMillis() - start) / 1000.0 , SATResults.size() + UNSATResults.size(), SATResults.size(), UNSATResults.size());
        return new SubsetCacheInitData(SATResults, UNSATResults);
	}

    @Data
    public static class SubsetCacheInitData {
        private final List<PrecacheSupersetEntry> SATResults;
        private final List<BitSet> UNSATResults;
    }

}