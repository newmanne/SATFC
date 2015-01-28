package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.database.CacheEntry;
import ca.ubc.cs.beta.stationpacking.database.ICacher;
import ca.ubc.cs.beta.stationpacking.database.PreCache;
import ca.ubc.cs.beta.stationpacking.database.StationPackingInstanceHasher;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCCachingParameters;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;

import com.google.common.hash.HashCode;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    public void cacheResult(CacheEntry entry) {
        final String jsonResult = JSONUtils.toString(entry);
        fJedis.set(getKey(fHasher.hash(new StationPackingInstance(entry.getDomains()))), jsonResult);
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
	public PreCacheInitData test() {
        log.info("Pulling precache data from redis");
        long start = System.currentTimeMillis();
		List<PreCache.SATBS> SATResults = new ArrayList<>();
		List<BitSet> UNSATResults = new ArrayList<>();
		final Set<String> keys = fJedis.keys("*");
		keys.forEach(key -> {
			CacheEntry cacheEntry = getSolverResultByKey(key, false).get();
			boolean UHFProblem = cacheEntry.getDomains().entrySet().stream().allMatch(entry -> StationPackingUtils.UHF_CHANNELS.containsAll(entry.getValue()));
			if (UHFProblem) {
                final SATResult result = cacheEntry.getSolverResult().getResult();
                if (result.equals(SATResult.SAT)) {
                    SATResults.add(new PreCache.SATBS(key, new StationPackingInstance(cacheEntry.getDomains()).toBitSet()));
                } else if (result.equals(SATResult.UNSAT)) {
                    UNSATResults.add(new StationPackingInstance(cacheEntry.getDomains()).toBitSet());
                }
            }
        });
        log.info("It took {}s to pull precache data from redis. Found {} applicable results. {} SAT and {} UNSAT", (System.currentTimeMillis() - start) / 1000.0 , SATResults.size() + UNSATResults.size(), SATResults.size(), UNSATResults.size());
        return new PreCacheInitData(SATResults, UNSATResults);
	}

    @Data
    public static class PreCacheInitData {
        private final List<PreCache.SATBS> SATResults;
        private final List<BitSet> UNSATResults;
    }

}