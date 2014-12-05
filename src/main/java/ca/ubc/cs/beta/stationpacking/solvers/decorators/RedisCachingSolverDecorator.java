package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCCachingParameters;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.google.common.hash.HashCode;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import java.util.Optional;

/**
 * Created by newmanne on 02/12/14.
 */
@Slf4j
public class RedisCachingSolverDecorator extends CachingSolverDecorator {
    private final Jedis fJedis;

    /**
     * @param aSolver                - decorated ISolver.
     * @param satfcCachingParameters
     * @param aGraphHash
     */
    public RedisCachingSolverDecorator(ISolver aSolver, SATFCCachingParameters satfcCachingParameters, String aGraphHash) {
        super(aSolver, satfcCachingParameters, aGraphHash);
        fJedis = satfcCachingParameters.getJedis();
    }

    @Override
    protected void cacheResult(CacheEntry entry) {
        final HashCode hash = hash(entry.getDomains());
        final String jsonResult = JSONUtils.toString(entry);
        fJedis.set(getKey(hash), jsonResult);
    }

    @Override
    protected Optional<CacheEntry> getSolverResultFromCache(StationPackingInstance aInstance) {
        final HashCode hash = hash(aInstance);
        final String key = getKey(hash);
        log.info("Asking redis for entry " + key);
        final String value = fJedis.get(key);
        final Optional<CacheEntry> result;
        if (value != null) {
            final CacheEntry cacheEntry = JSONUtils.toObject(value, CacheEntry.class);
            log.info("Cache hit! Result is " + cacheEntry.getSolverResult().getResult());
            result = Optional.of(cacheEntry);
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private String getKey(HashCode hashCode) {
        return "SATFC:" + hashCode.toString();
    }
}