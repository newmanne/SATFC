package ca.ubc.cs.beta.stationpacking.solvers.underconstrained;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2015-08-09.
 * A decorator around an underconstrained station finder that keeps looping over the decorated finder. Once you remove
 * some underconstrained stations, others become underconstrained (conditional on the others being removed)
 */
@Slf4j
public class ConditionallyUnderconstrainedStationFinder implements IUnderconstrainedStationFinder {

    final IUnderconstrainedStationFinder decoratedFinder;
    final IConstraintManager constraintManager;

    public ConditionallyUnderconstrainedStationFinder(IUnderconstrainedStationFinder decoratedFinder, IConstraintManager constraintManager) {
        this.decoratedFinder = decoratedFinder;
        this.constraintManager = constraintManager;
    }

    @Override
    public Set<Station> getUnderconstrainedStations(Map<Station, Set<Integer>> domains, ITerminationCriterion criterion, Set<Station> stationsToCheck) {
        final Set<Station> underconstrainedStations = new HashSet<>();
        final Map<Station, Set<Integer>> domainsCopy = new HashMap<>(domains);
        final Set<Station> stationsToRecheck = new HashSet<>(stationsToCheck);
        int roundCounter = 0;
        while (!stationsToRecheck.isEmpty()) {
            final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domainsCopy, constraintManager);
            final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(constraintGraph);
            final Set<Station> roundUnderconstrainedStations = decoratedFinder.getUnderconstrainedStations(domainsCopy, criterion, stationsToRecheck);
            stationsToRecheck.clear();
            boolean changed = underconstrainedStations.addAll(roundUnderconstrainedStations);
            if (changed) {
                domainsCopy.keySet().removeAll(roundUnderconstrainedStations);
                // You only need to recheck a station that might be underconstrained because some of his neigbhours have disappeared
                stationsToRecheck.addAll(roundUnderconstrainedStations.stream().map(neighborIndex::neighborsOf).flatMap(Collection::stream).collect(Collectors.toSet()));
            }
            log.info("Found {} new underconstrained stations in round {}", roundUnderconstrainedStations.size(), roundCounter++);
        }
        return underconstrainedStations;
    }

}
