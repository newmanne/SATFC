package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.SubsetCache;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import lombok.extern.slf4j.Slf4j;

import java.util.BitSet;
import java.util.Optional;

/**
* Created by newmanne on 28/01/15.
*/
@Slf4j
public class SubsetCacheUNSATDecorator extends ASolverDecorator {
    private final SubsetCache subsetCache;

    public SubsetCacheUNSATDecorator(ISolver aSolver, SubsetCache aSubsetCache) {
        super(aSolver);
        this.subsetCache = aSubsetCache;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        Watch watch = Watch.constructAutoStartWatch();
        log.trace("Converting instance to bitset");
        final BitSet aBitSet = aInstance.toBitSet();

        // test unsat cache - if any subset of the problem is UNSAT, then the whole problem is UNSAT
        final Optional<BitSet> subset = subsetCache.findSubset(aBitSet);
        final SolverResult result;
        if (subset.isPresent()) {
            log.info("Found a subset in the UNSAT cache - declaring problem UNSAT");
            watch.stop();
            result = new SolverResult(SATResult.UNSAT, watch.getElapsedTime());
        } else {
            result = super.solve(aInstance, aTerminationCriterion, aSeed);
        }
        return result;
    }
}
