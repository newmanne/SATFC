package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;

/**
* Created by newmanne on 28/01/15.
*/
@Slf4j
public class SupersetCacheSATDecorator extends ASolverDecorator {

    private final ContainmentCacheProxy proxy;
    private final ICacher.CacheCoordinate coordinate;

    public SupersetCacheSATDecorator(ISolver aSolver, ContainmentCacheProxy proxy, ICacher.CacheCoordinate coordinate) {
        super(aSolver);
        this.proxy = proxy;
        this.coordinate = coordinate;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Watch watch = Watch.constructAutoStartWatch();

        // test sat cache - supersets of the problem that are SAT directly correspond to solutions to the current problem!
        final SolverResult result;
        final ContainmentCache.ContainmentCacheSATResult containmentCacheSATResult = proxy.proveSATBySuperset(aInstance);
        if (containmentCacheSATResult.isValid()) {
            final Map<Integer, Set<Station>> assignment = containmentCacheSATResult.getResult();
            log.info("Found a superset in the SAT cache - declaring result SAT");
            final Map<Integer, Set<Station>> reducedAssignment = Maps.newHashMap();
            for (Integer channel : assignment.keySet()) {
                assignment.get(channel).stream().filter(station -> aInstance.getStations().contains(station)).forEach(station -> {
                    if (reducedAssignment.get(channel) == null) {
                        reducedAssignment.put(channel, Sets.newHashSet());
                    }
                    reducedAssignment.get(channel).add(station);
                });
            }
            result = new SolverResult(SATResult.SAT, watch.getElapsedTime(), reducedAssignment);
            SATFCMetrics.postEvent(new SATFCMetrics.SolvedByEvent(aInstance.getName(), SATFCMetrics.SolvedByEvent.SUPERSET_CACHE, result.getResult()));
            SATFCMetrics.postEvent(new SATFCMetrics.JustifiedByCacheEvent(aInstance.getName(), containmentCacheSATResult.getKey()));
        } else {
            final double preTime = watch.getElapsedTime();
            final SolverResult decoratedResult = fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
            result = SolverResult.addTime(decoratedResult, preTime);
        }
        return result;
    }

}
