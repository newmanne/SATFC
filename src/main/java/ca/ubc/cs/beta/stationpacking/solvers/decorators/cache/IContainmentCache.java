package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheEntry;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.*;

/**
 * Created by newmanne on 01/03/15.
 */
public interface IContainmentCache {

    ContainmentCacheResult findSubset(final BitSet bitSet);

    ContainmentCacheResult findSuperset(final BitSet bitSet);

    @Data
    @AllArgsConstructor
    public static class ContainmentCacheSATReply {
        private boolean valid;
        private SATResult result;
        private Map<Integer, Set<Station>> assignment;
    }

}