package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheEntry;
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

    public enum QueryType {
        SUPERSET,
        SUBSET
    }

    @Data
    public static class ContainmentCacheResult {
        private final Optional<String> key;
    }

    @Data
    @AllArgsConstructor
    public static class ContainmentCacheReply {
        private boolean valid;
        private String key;
        private SATResult result;
        private Map<Integer, Set<Station>> assignment;
    }

}
