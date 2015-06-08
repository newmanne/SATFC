package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by pcernek on 5/8/15.
 */
@Slf4j
public class ArcConsistencyEnforcerDecorator extends ASolverDecorator {

    private final IConstraintManager constraintManager;
    public final static MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    /**
     * @param aSolver           - decorated ISolver.
     * @param constraintManager
     */
    public ArcConsistencyEnforcerDecorator(ISolver aSolver, IConstraintManager constraintManager) {
        super(aSolver);
        this.constraintManager = constraintManager;
    }

    private BigInteger searchSpaceSize(Map<Station, Set<Integer>> domains) {
        return domains.values().stream().map(domain -> BigInteger.valueOf(domain.size())).reduce(BigInteger.ONE, BigInteger::multiply);
    }

    private long domainSize(Map<Station, Set<Integer>> domains) {
        return domains.values().stream().mapToInt(Set::size).sum();
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Watch watch = Watch.constructAutoStartWatch();
//        final BigInteger initialSearchSpaceSize = searchSpaceSize(aInstance.getDomains());
        final Map<Station, Set<Integer>> reducedDomains = AC3(aInstance);
        if (reducedDomains.values().stream().anyMatch(Set::isEmpty)) {
            return new SolverResult(SATResult.UNSAT, watch.getElapsedTime());
        } else {
            final StationPackingInstance reducedInstance = new StationPackingInstance(reducedDomains, aInstance.getPreviousAssignment(), aInstance.getMetadata());
//            final BigInteger afterSearchSpaceSize = searchSpaceSize(reducedInstance.getDomains());
//            final BigDecimal bd = new BigDecimal(afterSearchSpaceSize, MATH_CONTEXT).divide(new BigDecimal(initialSearchSpaceSize, MATH_CONTEXT), MATH_CONTEXT);
//            int newScale = 4-bd.precision()+bd.scale();
//            BigDecimal bd2 = bd.setScale(newScale, RoundingMode.HALF_UP);
//            log.info("Domains reduced from {} to {}. The reduced search space is a {}% of the initial space", initialSearchSpaceSize, afterSearchSpaceSize, bd2);
            return fDecoratedSolver.solve(reducedInstance, aTerminationCriterion, aSeed);
        }
    }

    /**
     * Will fail at the first indication of inconsistency.
     *
     * @param instance
     * @return
     */
    private Map<Station, Set<Integer>> AC3(StationPackingInstance instance) {
        final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(ConstraintGrouper.getConstraintGraph(instance, constraintManager));
        final LinkedBlockingQueue<Pair<Station, Station>> workList = getInterferingStationPairs(neighborIndex, instance);
        final Map<Station, Set<Integer>> reducedDomains = new HashMap<>(instance.getDomains());
        while (!workList.isEmpty()) {
            final Pair<Station, Station> pair = workList.poll();
            if (arcReduce(pair, reducedDomains)) {
                final Station referenceStation = pair.getLeft();
                if (reducedDomains.get(referenceStation).isEmpty()) {
                    log.info("Reduced a domain to empty! Problem is solved UNSAT");
                    return reducedDomains;
                } else {
                    reenqueueAllAffectedPairs(workList, pair, neighborIndex);
                }
            }
        }
        return reducedDomains;
    }

    private void reenqueueAllAffectedPairs(Queue<Pair<Station, Station>> interferingStationPairs,
                                           Pair<Station, Station> modifiedPair, NeighborIndex<Station, DefaultEdge> neighborIndex) {
        final Station referenceStation = modifiedPair.getLeft();
        final Station modifiedNeighbor = modifiedPair.getRight();

        neighborIndex.neighborsOf(referenceStation).stream().filter(neighbor -> neighbor != modifiedNeighbor).forEach(neighbor -> {
            interferingStationPairs.add(Pair.of(referenceStation, neighbor));
            interferingStationPairs.add(Pair.of(neighbor, referenceStation));
        });
    }

    private LinkedBlockingQueue<Pair<Station, Station>> getInterferingStationPairs(NeighborIndex<Station, DefaultEdge> neighborIndex,
                                                                             StationPackingInstance instance) {
        // TODO: do you need both (x,y) AND (y, x)?
        final LinkedBlockingQueue<Pair<Station, Station>> workList = new LinkedBlockingQueue<>();
        for (Station referenceStation : instance.getStations()) {
            for (Station neighborStation : neighborIndex.neighborsOf(referenceStation)) {
                workList.add(Pair.of(referenceStation, neighborStation));
            }
        }
        return workList;
    }

    private boolean arcReduce(Pair<Station, Station> pair, Map<Station, Set<Integer>> domains) {
        boolean change = false;
        final Station xStation = pair.getLeft();
        final Station yStation = pair.getRight();
        final List<Integer> xValuesToPurge = new ArrayList<>();
        for (int vx : domains.get(xStation)) {
            if (channelViolatesArcConsistency(xStation, vx, yStation, domains)) {
                log.info("Purging channel {} from station {}'s domain", vx, xStation.getID());
                xValuesToPurge.add(vx);
                change = true;
            }
        }
        domains.get(xStation).removeAll(xValuesToPurge);
        return change;
    }

    private boolean channelViolatesArcConsistency(Station xStation, int vx, Station yStation, Map<Station, Set<Integer>> domains) {
        for (int vy : domains.get(yStation)) {
            final Map<Integer, Set<Station>> assignment = new HashMap<>();
            assignment.put(vx, Sets.newHashSet(xStation));
            assignment.putIfAbsent(vy, new HashSet<>());
            assignment.get(vy).add(yStation);
            if (constraintManager.isSatisfyingAssignment(assignment)) {
                return false;
            }
        }
        return true;
    }

}