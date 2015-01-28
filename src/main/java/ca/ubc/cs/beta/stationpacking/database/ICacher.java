package ca.ubc.cs.beta.stationpacking.database;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.RedisCacher;
import com.google.common.hash.HashCode;

import java.util.Optional;

/**
 * Created by newmanne on 1/25/15.
 */
public interface ICacher {

    Optional<CacheEntry> getSolverResultFromCache(StationPackingInstance stationPackingInstance);
    Optional<CacheEntry> getSolverResultByKey(String key);
    void cacheResult(CacheEntry entry);

    RedisCacher.PreCacheInitData test();
}
