package ca.ubc.cs.beta.stationpacking.test;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;

import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;

/**
 * A mock ConstraintManager useful for running tests where the focus is on the shape of interference constraint graphs
 *  between stations.
 * @author pcernek
 */
public class GraphBackedConstraintManager implements IConstraintManager {

    private final NeighborIndex<Station, DefaultEdge> coInterferingStationNeighborIndex;
    private final NeighborIndex<Station, DefaultEdge> adjInterferingStationNeighborIndex;
    
    private final SimpleGraph<Station, DefaultEdge> coInterferenceGraph;
    private final SimpleGraph<Station, DefaultEdge> adjInterferenceGraph;

    /**
     * Creates a constraint manager backed by the given interference graphs.
     * @param coInterferingStationGraph - the graph representing CO interfering stations.
     * @param adjInterferingStationGraph - the graph representing ADJ interfering stations.
     */
    public GraphBackedConstraintManager(SimpleGraph<Station, DefaultEdge> coInterferingStationGraph,
                                        SimpleGraph<Station, DefaultEdge> adjInterferingStationGraph) {

    	coInterferenceGraph = coInterferingStationGraph;
    	adjInterferenceGraph = adjInterferingStationGraph;
    	
    	coInterferingStationNeighborIndex = new NeighborIndex<>(coInterferingStationGraph);
        adjInterferingStationNeighborIndex = new NeighborIndex<>(adjInterferingStationGraph);

    }

    @Override
    public Set<Station> getCOInterferingStations(Station aStation, int aChannel) {
    	if (this.coInterferenceGraph.containsVertex(aStation)) {
    		return this.coInterferingStationNeighborIndex.neighborsOf(aStation);
    	}
    	return Collections.emptySet();
    }


    @Override
    public Set<Station> getADJplusInterferingStations(Station aStation, int aChannel) {
    	if (this.adjInterferenceGraph.containsVertex(aStation)) {
    		return this.adjInterferingStationNeighborIndex.neighborsOf(aStation);
    	}
    	return Collections.emptySet();
    }

    @Override
    public boolean isSatisfyingAssignment(Map<Integer, Set<Station>> aAssignment) {

        if (violatesCOconstraints(aAssignment)) return false;

        if (violatesADJConstraints(aAssignment)) return false;

        return true;
    }

    private boolean violatesADJConstraints(Map<Integer, Set<Station>> aAssignment) {
        for (Integer channel: aAssignment.keySet()) {
            for (Station station : aAssignment.get(channel)) {

                Set<Station> stationsOnNextChannel = aAssignment.get(channel + 1);

                // If (channel + 1) is not contained in the assignment, then there can be no adjacency interference
                if (stationsOnNextChannel == null)
                    continue;

                for (Station neighbor : adjInterferingStationNeighborIndex.neighborsOf(station))
                    if (stationsOnNextChannel.contains(neighbor))
                        return true;
            }
        }

        return false;
    }

    private boolean violatesCOconstraints(Map<Integer, Set<Station>> aAssignment) {
        for (Integer channel : aAssignment.keySet()) {
            Set<Station> stationsOnChannel = aAssignment.get(channel);
            for (Station station : stationsOnChannel) {
                for (Station neighbor : coInterferingStationNeighborIndex.neighborsOf(station))
                    if (stationsOnChannel.contains(neighbor))
                        return true;
            }
        }

        return false;
    }

    @Override
    public String getHashCode() {
        return this.toString();
    }

}
