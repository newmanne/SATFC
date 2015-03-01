package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics.SolvedByEvent;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import lombok.extern.slf4j.Slf4j;

import java.util.BitSet;
import java.util.Optional;

/**
* Created by newmanne on 28/01/15.
*/
@Slf4j
public class SubsetCacheUNSATDecorator extends ASolverDecorator {
    private final IContainmentCache supersetSubsetCache;

    public SubsetCacheUNSATDecorator(ISolver aSolver, IContainmentCache aSupersetSubsetCache) {
        super(aSolver);
        this.supersetSubsetCache = aSupersetSubsetCache;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        Watch watch = Watch.constructAutoStartWatch();
        // test unsat cache - if any subset of the problem is UNSAT, then the whole problem is UNSAT
        final IContainmentCache.ContainmentCacheResult subset = supersetSubsetCache.findSubset(aInstance.getStations().stream().map(Station::getID).collect(GuavaCollectors.toImmutableList()));
        SATFCMetrics.postEvent(new SATFCMetrics.TimingEvent(aInstance.getName(), SATFCMetrics.TimingEvent.FIND_SUBSET, watch.getElapsedTime()));
        final SolverResult result;
        if (subset.getKey().isPresent()) {
            log.info("Found a subset in the UNSAT cache - declaring problem UNSAT");
            result = new SolverResult(SATResult.UNSAT, watch.getElapsedTime());
            SATFCMetrics.postEvent(new SATFCMetrics.SolvedByEvent(aInstance.getName(), SolvedByEvent.SUBSET_CACHE, result.getResult()));
            SATFCMetrics.postEvent(new SATFCMetrics.JustifiedByCacheEvent(aInstance.getName(), subset.getKey().get()));
        } else {
            final double preTime = watch.getElapsedTime();
            final SolverResult decoratorResult = super.solve(aInstance, aTerminationCriterion, aSeed);
            result = SolverResult.addTime(decoratorResult, preTime);
        }
        return result;
    }
}
