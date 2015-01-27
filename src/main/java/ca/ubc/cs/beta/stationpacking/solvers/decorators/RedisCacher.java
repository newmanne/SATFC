package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.database.CacheEntry;
import ca.ubc.cs.beta.stationpacking.database.ICacher;
import ca.ubc.cs.beta.stationpacking.database.StationPackingInstanceHasher;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCCachingParameters;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;

import com.google.common.hash.HashCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import java.util.Optional;

/**
 * Created by newmanne on 02/12/14.
 * Interfaces with redis to store and retrieve CacheEntry's
 */
@Slf4j
@RequiredArgsConstructor
public class RedisCacher implements ICacher {

    private final Jedis fJedis;
    private final StationPackingInstanceHasher fHasher;
    private final String INTEREFERENCE_NAME = "021814SC3M";
    
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

    
    public Optional<CacheEntry> getSolverResultFromCache(HashCode hash) {
        final String key = getKey(hash);
        log.info("Asking redis for entry " + key);
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

    private String getKey(HashCode hashCode) {
        return "SATFC:" + INTEREFERENCE_NAME + ":" + hashCode.toString();
    }

}