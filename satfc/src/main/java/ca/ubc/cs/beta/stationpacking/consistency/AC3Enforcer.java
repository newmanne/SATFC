package ca.ubc.cs.beta.stationpacking.consistency;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
* Created by newmanne on 10/06/15.
*/
@Slf4j
public class AC3Enforcer {

    private final IConstraintManager constraintManager;

    public AC3Enforcer(IConstraintManager constraintManager) {
        this.constraintManager = constraintManager;
    }

    /**
     * Will fail at the first indication of inconsistency.
     *
     * @param instance
     * @return
     */
    public AC3Output AC3(StationPackingInstance instance) {
        final Map<Station, Set<Integer>> reducedDomains = new HashMap<>(instance.getDomains());
        final AC3Output output = new AC3Output(reducedDomains);
        final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(ConstraintGrouper.getConstraintGraph(instance, constraintManager));
        final LinkedBlockingQueue<Pair<Station, Station>> workList = getInterferingStationPairs(neighborIndex, instance);
        while (!workList.isEmpty()) {
            final Pair<Station, Station> pair = workList.poll();
            if (arcReduce(pair, output)) {
                final Station referenceStation = pair.getLeft();
                if (reducedDomains.get(referenceStation).isEmpty()) {
                    log.debug("Reduced a domain to empty! Problem is solved UNSAT");
                    output.setNoSolution(true);
                    return output;
                } else {
                    reenqueueAllAffectedPairs(workList, pair, neighborIndex);
                }
            }
        }
        return output;
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
        final LinkedBlockingQueue<Pair<Station, Station>> workList = new LinkedBlockingQueue<>();
        for (Station referenceStation : instance.getStations()) {
            for (Station neighborStation : neighborIndex.neighborsOf(referenceStation)) {
                workList.add(Pair.of(referenceStation, neighborStation));
            }
        }
        return workList;
    }

    private boolean arcReduce(Pair<Station, Station> pair, AC3Output output) {
        boolean change = false;
        final Map<Station, Set<Integer>> domains = output.getReducedDomains();
        final Station xStation = pair.getLeft();
        final Station yStation = pair.getRight();
        final List<Integer> xValuesToPurge = new ArrayList<>();
        for (int vx : domains.get(xStation)) {
            if (channelViolatesArcConsistency(xStation, vx, yStation, domains)) {
                log.debug("Purging channel {} from station {}'s domain", vx, xStation.getID());
                output.setNumReducedChannels(output.getNumReducedChannels() + 1);
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
