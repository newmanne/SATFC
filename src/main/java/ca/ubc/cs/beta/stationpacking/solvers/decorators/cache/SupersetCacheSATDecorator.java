package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.CacheEntry;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics.SolvedByEvent;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
* Created by newmanne on 28/01/15.
*/
@Slf4j
public class SupersetCacheSATDecorator extends ASolverDecorator {

    private final IContainmentCache containmentCache;
    private final ICacher cacher;

    public SupersetCacheSATDecorator(ISolver aSolver, IContainmentCache containmentCache, ICacher cacher) {
        super(aSolver);
        this.containmentCache = containmentCache;
        this.cacher = cacher;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Watch watch = Watch.constructAutoStartWatch();
        log.trace("Converting instance to bitset");


        // test sat cache - supersets of the problem that are SAT directly correspond to solutions to the current problem!
        SATFCMetrics.postEvent(new SATFCMetrics.TimingEvent(aInstance.getName(), SATFCMetrics.TimingEvent.FIND_SUPERSET, watch.getElapsedTime()));
        final SolverResult result;


        final IContainmentCache.ContainmentCacheResult superset = containmentCache.findSuperset(aInstance.getStations().stream().map(Station::getID).collect(GuavaCollectors.toImmutableList()));

        if (superset.getKey().isPresent()) {
            log.info("Found a superset in the SAT cache - declaring result SAT");
            // yay! problem is SAT! Now let's look it up
            final String key = superset.getKey().get();
            final CacheEntry entry = cacher.getSolverResultByKey(key).get();

            // convert the answer to that problem into an answer for this problem
            final ImmutableMap<Integer, Set<Station>> assignment = entry.getSolverResult().getAssignment();
            final Map<Integer, Set<Station>> reducedAssignment = Maps.newHashMap();
            for (Integer channel : assignment.keySet()) {
                for (Station station : assignment.get(channel)) {
                    if (aInstance.getStations().contains(station)) {
                        if (reducedAssignment.get(channel) == null) {
                            reducedAssignment.put(channel, Sets.newHashSet());
                        }
                        reducedAssignment.get(channel).add(station);
                    }
                }
            }

            result = new SolverResult(SATResult.SAT, watch.getElapsedTime(), reducedAssignment);
            SATFCMetrics.postEvent(new SATFCMetrics.SolvedByEvent(aInstance.getName(), SolvedByEvent.SUPERSET_CACHE, result.getResult()));
            SATFCMetrics.postEvent(new SATFCMetrics.JustifiedByCacheEvent(aInstance.getName(), key));
        } else {
            final double preTime = watch.getElapsedTime();
            final SolverResult decoratedResult = fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
            result = SolverResult.addTime(decoratedResult, preTime);
        }
        return result;
    }

}
