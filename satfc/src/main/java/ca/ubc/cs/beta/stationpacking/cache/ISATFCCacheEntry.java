package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;

import java.util.BitSet;
import java.util.Map;

/**
* Created by newmanne on 27/10/15.
*/
public interface ISATFCCacheEntry {

    BitSet getBitSet();

    SATResult getResult();

}
