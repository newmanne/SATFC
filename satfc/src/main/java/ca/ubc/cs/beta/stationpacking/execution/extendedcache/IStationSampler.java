package ca.ubc.cs.beta.stationpacking.execution.extendedcache;

import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;

import java.util.BitSet;

/**
 * Created by emily404 on 6/4/15.
 */
public interface IStationSampler {
    Integer sample(BitSet bitSet);
}
