package ca.ubc.cs.beta.stationpacking.cache;

import java.util.List;
import java.util.Optional;

import lombok.Data;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * Created by newmanne on 1/25/15.
 */
public interface ICacher {

    void cacheResult(CacheCoordinate cacheCoordinate, SATCacheEntry entry);

    public static interface IContainmentCacher extends ICacher {
        Optional<SATCacheEntry> getSolverResultByKey(CacheCoordinate coordinate);
        RedisCacher.SubsetCacheInitData getSubsetCacheData();
    }

    @Data
    public static class CacheCoordinate {

        private final String domainHash;
        private final String interferenceHash;
        private final int clearingTarget;
        private final String problem;

        public CacheCoordinate(String domainHash, String interferenceHash, int clearingTarget, StationPackingInstance instance) {
            this.domainHash = domainHash;
            this.interferenceHash = interferenceHash;
            this.clearingTarget = clearingTarget;
            this.problem = new String(instance.toBitSet().toByteArray());
        }

        public String toKey() {
            return Joiner.on(":").join(ImmutableList.of("SATFC", domainHash, interferenceHash, clearingTarget, problem));
        }

        public CacheCoordinate(String key) {
            final List<String> strings = Splitter.on(':').splitToList(key);
            this.domainHash = strings.get(0);
            this.interferenceHash = strings.get(1);
            this.clearingTarget = Integer.parseInt(strings.get(2));
            this.problem = strings.get(3);
        }

    }

}
