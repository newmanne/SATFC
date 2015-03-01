package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache.ContainmentCacheEntry;
import lombok.Data;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/**
 * Created by newmanne on 01/03/15.
 */
public interface IContainmentCache {

    ContainmentCacheResult findSubset(final List<Integer> stations);

    ContainmentCacheResult findSuperset(final List<Integer> stations);

    public enum QueryType {
        SUPERSET,
        SUBSET
    }

    @Data
    public static class ContainmentCacheResult {
        private final Optional<String> key;
    }

}
