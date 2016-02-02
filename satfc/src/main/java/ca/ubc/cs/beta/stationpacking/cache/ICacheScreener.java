package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheUNSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ISatisfiabilityCache;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import lombok.RequiredArgsConstructor;

/**
 * Created by newmanne on 01/02/16.
 * Filter entries (determine whether or not they should be cached)
 */
public interface ICacheScreener {

    /**
     * @Return True if should cache
     */
    boolean screen(CacheCoordinate coordinate, StationPackingInstance instance, SolverResult result);

    @RequiredArgsConstructor
    public static class NewInfoScreener implements ICacheScreener {

        private final ICacheLocator cacheLocator;

        @Override
        public boolean screen(CacheCoordinate coordinate, StationPackingInstance instance, SolverResult result) {
            final ISatisfiabilityCache cache = cacheLocator.locate(coordinate);
            final boolean containsNewInfo;
            if (result.getResult().equals(SATResult.SAT)) {
                containsNewInfo = !cache.proveSATBySuperset(instance).isValid();
            } else if (result.getResult().equals(SATResult.UNSAT)) {
                containsNewInfo = !cache.proveUNSATBySubset(instance).isValid();
            } else {
                throw new IllegalStateException("Tried adding a result that was neither SAT or UNSAT");
            }
            return containsNewInfo;
        }
    }

}
