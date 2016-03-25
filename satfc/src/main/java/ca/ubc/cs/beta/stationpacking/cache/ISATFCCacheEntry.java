package ca.ubc.cs.beta.stationpacking.cache;

import java.util.BitSet;

import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;

/**
* Created by newmanne on 27/10/15.
*/
public interface ISATFCCacheEntry {

    BitSet getBitSet();

    SATResult getResult();

}
