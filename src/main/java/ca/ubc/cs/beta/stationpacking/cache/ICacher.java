package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;

import java.util.Optional;

/**
 * Created by newmanne on 1/25/15.
 */
public interface ICacher {

    Optional<CacheEntry> getSolverResultFromCache(StationPackingInstance stationPackingInstance);
    Optional<CacheEntry> getSolverResultByKey(String key);
    void cacheResult(CacheEntry entry);

    RedisCacher.PreCacheInitData getPreCacheData();
}
