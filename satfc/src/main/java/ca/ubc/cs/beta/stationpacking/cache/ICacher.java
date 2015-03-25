package ca.ubc.cs.beta.stationpacking.cache;

import java.util.*;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.AllArgsConstructor;
import lombok.Data;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Created by newmanne on 1/25/15.
 */
public interface ICacher {

    void cacheResult(CacheCoordinate cacheCoordinate, StationPackingInstance instance, SolverResult result);

    /**
     * This class determines which cache is accessed
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheCoordinate {

        private String domainHash;
        private String interferenceHash;

        public static CacheCoordinate fromKey(String key) {
            final List<String> strings = Splitter.on(":").splitToList(key);
            return new CacheCoordinate(strings.get(2), strings.get(3));
        }

        public String toKey(SATResult result, StationPackingInstance instance) {
            Preconditions.checkArgument(result.equals(SATResult.SAT) || result.equals(SATResult.UNSAT));
            return Joiner.on(":").join(ImmutableList.of("SATFC", result, domainHash, interferenceHash, StationPackingInstanceHasher.hash(instance)));
        }

    }

    public static class StationPackingInstanceHasher {

        // hashing function
        private static final HashFunction fHashFuction = Hashing.murmur3_32();

        public static HashCode hash(StationPackingInstance aInstance) {
            final Watch watch = Watch.constructAutoStartWatch();
            final HashCode hash = fHashFuction.newHasher()
                    .putString(aInstance.toString(), Charsets.UTF_8)
                    .hash();
            SATFCMetrics.postEvent(new SATFCMetrics.TimingEvent(aInstance.getName(), SATFCMetrics.TimingEvent.HASHING, watch.getElapsedTime()));
            return hash;
        }

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SATCacheEntry {
        private Map<String, Object> metadata;
        private Map<Integer, Set<Station>> assignment;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UNSATCacheEntry {
        private Map<String, Object> metadata;
        Map<Station, Set<Integer>> domains;
    }

}
