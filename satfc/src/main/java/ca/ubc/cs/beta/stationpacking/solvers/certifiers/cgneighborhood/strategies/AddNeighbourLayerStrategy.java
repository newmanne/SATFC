package ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
* Created by newmanne on 27/07/15.
*/
@Slf4j
public class AddNeighbourLayerStrategy implements IStationAddingStrategy {

    private final int maxLayers;
    private final IConstraintManager fConstraintManager;

    public AddNeighbourLayerStrategy(IConstraintManager constraintManager, int maxLayers) {
        this.fConstraintManager = constraintManager;
        Preconditions.checkArgument(maxLayers > 0, "max layers must be > 0");
        this.maxLayers = maxLayers;
    }

    public AddNeighbourLayerStrategy(IConstraintManager constraintManager) {
        this(constraintManager, Integer.MAX_VALUE);
    }

    @Override
    public Iterable<Set<Station>> getStationsToPack(StationPackingInstance instance, Set<Station> missingStations) {
        log.debug("Building constraint graph.");
        final NeighborIndex<Station, DefaultEdge> neighbourIndex = new NeighborIndex<>(ConstraintGrouper.getConstraintGraph(instance.getDomains(), fConstraintManager));
        return () -> new AbstractIterator<Set<Station>>() {

            Set<Station> prev = new HashSet<>(missingStations);
            int currentLayer = 1;

            @Override
            protected Set<Station> computeNext() {
                if (currentLayer > maxLayers) {
                    return endOfData();
                }
                Set<Station> newToPack = Sets.union(prev, getNeighbours(prev));
                // If nothing new was added, we are done
                if (prev.equals(newToPack)) {
                    return endOfData();
                }
                currentLayer++;
                prev = newToPack;
                return newToPack;
            }

            private Set<Station> getNeighbours(Set<Station> stations) {
                return stations.stream().map(neighbourIndex::neighborsOf).flatMap(Collection::stream).collect(Collectors.toSet());
            }

        };
    }
}
