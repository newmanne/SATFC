package ca.ubc.cs.beta.stationpacking.cache;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * This class determines which cache is accessed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheCoordinate {

    private String domainHash;
    private String interferenceHash;

    // transform a redis key into a cache coordinate
    public static CacheCoordinate fromKey(String key) {
        final List<String> strings = Splitter.on(":").splitToList(key);
        return new CacheCoordinate(strings.get(2), strings.get(3));
    }

    // create a redis key from a coordinate, a result, and an instance
    public String toKey(SATResult result, StationPackingInstance instance) {
        Preconditions.checkArgument(result.equals(SATResult.SAT) || result.equals(SATResult.UNSAT));
        return Joiner.on(":").join(ImmutableList.of("SATFC", result, domainHash, interferenceHash, StationPackingInstanceHasher.hash(instance)));
    }

}
