package ca.ubc.cs.beta.stationpacking;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 21/04/15.
 */
public class StationPackingTestUtils {

    public static StationPackingInstance getSimpleInstance() {
        return new StationPackingInstance(ImmutableMap.of(new Station(1), ImmutableSet.of(1, 2)));
    }

    public static Map<Integer, Set<Station>> getSimpleInstanceAnswer() {
        return ImmutableMap.of(1, ImmutableSet.of(new Station(1)));
    }
}
