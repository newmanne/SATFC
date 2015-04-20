package ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATResult;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheUNSATResult;

/**
 * Created by newmanne on 19/04/15.
 */
public interface IContainmentCacheBundle {
    ContainmentCacheSATResult proveSATBySuperset(final StationPackingInstance aInstance);
    ContainmentCacheUNSATResult proveUNSATBySubset(final StationPackingInstance aInstance);
}
