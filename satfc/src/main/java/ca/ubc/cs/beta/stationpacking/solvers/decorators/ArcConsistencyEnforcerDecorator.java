package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;

/**
 * Created by pcernek on 5/8/15.
 */
public class ArcConsistencyEnforcerDecorator extends ASolverDecorator {

    private final IConstraintManager constraintManager;

    /**
     * @param aSolver           - decorated ISolver.
     * @param constraintManager
     */
    public ArcConsistencyEnforcerDecorator(ISolver aSolver, IConstraintManager constraintManager) {
        super(aSolver);
        this.constraintManager = constraintManager;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        return fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
    }

    /**
     * Will fail at the first indication of inconsistency.
     *
     * @param instance
     * @param constraintManager
     * @return
     */
    private boolean AC3(StationPackingInstance instance, IConstraintManager constraintManager) {
        NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(ConstraintGrouper.getConstraintGraph(instance, constraintManager));

        LinkedHashSet<Pair<Station, Station>> interferingStationPairs = getInterferingStationPairs(neighborIndex, instance);
        Iterator<Pair<Station, Station>> iterator = interferingStationPairs.iterator();

        while (iterator.hasNext()) {
            Pair<Station, Station> pair = iterator.next();
            iterator.remove();
            if (arcReduce(pair, instance, constraintManager)) {
                Station referenceStation = pair.getLeft();
                if (instance.getDomains().get(referenceStation).isEmpty()) {
                    return false;
                } else {
                    reenqueueAllAffectedPairs(interferingStationPairs, pair, neighborIndex);
                }
            }
        }

        return true;
    }

    private void reenqueueAllAffectedPairs(LinkedHashSet<Pair<Station, Station>> interferingStationPairs,
                                           Pair<Station, Station> modifiedPair, NeighborIndex<Station, DefaultEdge> neighborIndex) {
        Station referenceStation = modifiedPair.getLeft();
        Station modifiedNeighbor = modifiedPair.getRight();

        Set<Station> allOtherNeighbors = neighborIndex.neighborsOf(referenceStation);
        allOtherNeighbors.remove(modifiedNeighbor);

        for (Station neighbor : allOtherNeighbors) {
            interferingStationPairs.add(Pair.of(referenceStation, neighbor));
            interferingStationPairs.add(Pair.of(neighbor, referenceStation));
        }
    }

    private LinkedHashSet<Pair<Station, Station>> getInterferingStationPairs(NeighborIndex<Station, DefaultEdge> neighborIndex,
                                                                             StationPackingInstance instance) {
        LinkedHashSet<Pair<Station, Station>> interferingStationPairs = new LinkedHashSet<>();

        for (Station referenceStation : instance.getStations()) {
            for (Station neighborStation : neighborIndex.neighborsOf(referenceStation)) {
                interferingStationPairs.add(Pair.of(referenceStation, neighborStation));
            }
        }

        return interferingStationPairs;
    }

    private boolean arcReduce(Pair<Station, Station> pair, StationPackingInstance instance, IConstraintManager constraintManager) {
        boolean change = false;
        Station referenceStation = pair.getLeft();
        Station neighborStation = pair.getRight();
        for (Integer referenceChannel : instance.getDomains().get(referenceStation)) {
            if (channelViolatesArcConsistency(referenceStation, referenceChannel, neighborStation, instance, constraintManager)) {
                instance.getDomains().get(referenceStation).remove(referenceChannel);
                change = true;
            }
        }
        return change;
    }

    private boolean channelViolatesArcConsistency(Station referenceStation, Integer referenceChannel, Station neighborStation,
                                                  StationPackingInstance instance, IConstraintManager constraintManager) {
        for (Integer neighborChannel : instance.getDomains().get(neighborStation)) {
            Map<Integer, Set<Station>> tempAssignment = new HashMap<>();
            tempAssignment.put(referenceChannel, new HashSet<>());
            tempAssignment.put(neighborChannel, new HashSet<>());
            tempAssignment.get(referenceChannel).add(referenceStation);
            tempAssignment.get(neighborChannel).add(neighborStation);
            if (constraintManager.isSatisfyingAssignment(tempAssignment)) {
                return false;
            }
        }

        return true;
    }

    private LinkedHashSet<Pair<Station, Station>> getInterferingStationPairs(StationPackingInstance instance, IConstraintManager constraintManager) {
        LinkedHashSet<Pair<Station, Station>> interferingStationPairs = new LinkedHashSet<>();
        for (Station station : instance.getStations()) {
            for (Integer domainChannel : instance.getDomains().get(station)) {
                for (Station coInterferingNeighbor : constraintManager.getCOInterferingStations(station, domainChannel)) {
                    Pair<Station, Station> pair = Pair.of(station, coInterferingNeighbor);
                    interferingStationPairs.add(pair);
                }
                for (Station adjInterferingNeighbor : constraintManager.getADJplusInterferingStations(station, domainChannel)) {
                    Pair<Station, Station> pair = Pair.of(station, adjInterferingNeighbor);
                    interferingStationPairs.add(pair);
                }
            }
        }
        return interferingStationPairs;
    }

}