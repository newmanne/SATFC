package ca.ubc.cs.beta.stationpacking.test;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;

/**
 * Created by pcernek on 4/29/15.
 */
public abstract class GraphBackedConstraintManager implements IConstraintManager {

    private final NeighborIndex<Station, DefaultEdge> coInterferingStationNeighborIndex;
    private final NeighborIndex<Station, DefaultEdge> adjInterferingStationNeighborIndex;

    /**
     * Treats the station graph as representing both CO and ADJ interference constraints.
     * @param stationGraph - the graph that represents the configuration of constraints between stations
     *              that we want to test. Assumes that this graph has been fully configured with all relevant
     *              edges between nodes.
     */
    public GraphBackedConstraintManager(SimpleGraph<Station, DefaultEdge> stationGraph) {

        coInterferingStationNeighborIndex = new NeighborIndex<>(stationGraph);
        adjInterferingStationNeighborIndex = new NeighborIndex<>(stationGraph);
    }

    public GraphBackedConstraintManager(SimpleGraph<Station, DefaultEdge> coInterferingStationGraph,
                                        SimpleGraph<Station, DefaultEdge> adjInterferingStationGraph) {

        coInterferingStationNeighborIndex = new NeighborIndex<>(coInterferingStationGraph);
        adjInterferingStationNeighborIndex = new NeighborIndex<>(adjInterferingStationGraph);

    }

    /**
     * @param aStation - a (source) station of interest.
     * @param aChannel - any integer. This parameter is ignored (exists for compatibility with interface).
     * @return - a set of "conflicting stations" that is guaranteed to correspond to the shape of the graph with
     *  which this constraint manager was initialized.
     */
    @Override
    public Set<Station> getCOInterferingStations(Station aStation, int aChannel) {
        return this.coInterferingStationNeighborIndex.neighborsOf(aStation);
    }

    @Override
    public Set<Station> getADJplusInterferingStations(Station aStation, int aChannel) {
        return this.adjInterferingStationNeighborIndex.neighborsOf(aStation);
    }

}
