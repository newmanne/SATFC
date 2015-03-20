package ca.ubc.cs.beta.stationpacking.utils;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;

import java.util.BitSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 19/03/15.
 */
public class CacheUtils {

    public static BitSet toBitSet(StationPackingInstance aInstance) {
        final BitSet bitSet = new BitSet();
        aInstance.getStations().forEach(station -> bitSet.set(station.getID()));
        return bitSet;
    }

    public static BitSet toBitSet(Map<Integer, Set<Station>> answer) {
        final BitSet bitSet = new BitSet();
        answer.values().stream().forEach(stations -> stations.forEach(station-> bitSet.set(station.getID())));
        return bitSet;
    }

}
