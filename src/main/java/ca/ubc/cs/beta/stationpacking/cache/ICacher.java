package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;

import java.util.Date;
import java.util.Optional;

/**
 * Created by newmanne on 1/25/15.
 */
public interface ICacher {

    Optional<CacheEntry> getSolverResultFromCache(StationPackingInstance stationPackingInstance);
    Optional<CacheEntry> getSolverResultByKey(String key);
    void cacheResult(StationPackingInstance instance, CacheEntry entry);

    RedisCacher.SubsetCacheInitData getSubsetCacheData();
}
