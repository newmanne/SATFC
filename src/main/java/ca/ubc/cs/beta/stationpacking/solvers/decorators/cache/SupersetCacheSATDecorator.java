package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

/**
* Created by newmanne on 28/01/15.
*/
@Slf4j
public class SupersetCacheSATDecorator extends ASolverDecorator {

    private final IContainmentCache containmentCache;

    public SupersetCacheSATDecorator(ISolver aSolver, IContainmentCache containmentCache) {
        super(aSolver);
        this.containmentCache = containmentCache;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Watch watch = Watch.constructAutoStartWatch();
        log.trace("Converting instance to bitset");


        // test sat cache - supersets of the problem that are SAT directly correspond to solutions to the current problem!
        SATFCMetrics.postEvent(new SATFCMetrics.TimingEvent(aInstance.getName(), SATFCMetrics.TimingEvent.FIND_SUPERSET, watch.getElapsedTime()));
        final SolverResult result;

        final IContainmentCache.ContainmentCacheResult superset = containmentCache.findSuperset(aInstance.toBitSet());

        if (false) {
        	// TODO:!!!
        } else {
            final double preTime = watch.getElapsedTime();
            final SolverResult decoratedResult = fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
            result = SolverResult.addTime(decoratedResult, preTime);
        }
        return result;
    }

}
