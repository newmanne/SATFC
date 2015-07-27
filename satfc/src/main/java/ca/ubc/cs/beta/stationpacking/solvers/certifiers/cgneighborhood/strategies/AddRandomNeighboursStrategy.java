package ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.stream.Collectors;

/**
* Created by newmanne on 27/07/15.
*/
@Slf4j
public class AddRandomNeighboursStrategy implements IStationAddingStrategy {

    private final IConstraintManager fConstraintManager;
    private final int numNeigbhoursToAdd;
    private final Random random;

    public AddRandomNeighboursStrategy(IConstraintManager constraintManager, int numNeigbhoursToAdd, long seed) {
        this.fConstraintManager = constraintManager;
        this.numNeigbhoursToAdd = numNeigbhoursToAdd;
        this.random = new Random(seed);
    }

    @Override
    public Iterable<Set<Station>> getStationsToPack(StationPackingInstance instance, Set<Station> missingStations) {
        log.debug("Building constraint graph.");
        final NeighborIndex<Station, DefaultEdge> neighbourIndex = new NeighborIndex<>(ConstraintGrouper.getConstraintGraph(instance.getDomains(), fConstraintManager));
        return () -> new AbstractIterator<Set<Station>>() {

            Set<Station> prev = new HashSet<>(missingStations);

            @Override
            protected Set<Station> computeNext() {
                List<Station> nonAddedNeighbours = new ArrayList<>(getNoneAddedNeighbours(prev));
                if (nonAddedNeighbours.isEmpty()) {
                    log.debug("No more stations to add");
                    return endOfData();
                }
                Collections.shuffle(nonAddedNeighbours, random);
                final Set<Station> stationsToAdd = new HashSet<>(nonAddedNeighbours.subList(0, Math.min(numNeigbhoursToAdd, nonAddedNeighbours.size())));
                log.debug("Adding {} new stations", stationsToAdd.size());
                prev = Sets.union(prev, stationsToAdd);
                return prev;
            }

            private Set<Station> getNoneAddedNeighbours(Set<Station> stations) {
                return Sets.difference(stations.stream().map(neighbourIndex::neighborsOf).flatMap(Collection::stream).collect(Collectors.toSet()), stations);
            }

        };
    }
}
