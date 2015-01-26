package ca.ubc.cs.beta.stationpacking.database;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import com.google.common.hash.HashCode;

import java.util.Optional;

/**
 * Created by newmanne on 1/25/15.
 */
public interface ICacher {

    Optional<CacheEntry> getSolverResultFromCache(StationPackingInstance stationPackingInstance);
    void cacheResult(CacheEntry entry);

}
