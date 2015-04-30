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

    private final NeighborIndex<Station, DefaultEdge> stationNeighborIndex;

    /**
     * Assumes that the use case in which we're interested is that in which we're growing neighbors out
     *  from a single initial station; i.e., instance.getStations() contains all the same stations as
     *  instance.getPreviousAssignment().keySet(), plus focalStation.
     *
     * @param graph - the graph that represents the configuration of constraints between stations
     *              that we want to test. Assumes that this graph has been fully configured with all relevant
     *              edges between nodes.
     * @param instance - the instance of a station packing problem that tells us the stations in which we're interested;
     *                 IMPORTANT: Assumes that the number of stations in the instance is *at least* as many as the
     *                 number of nodes in the graph.
     * @param focalStation - the station from which we wish to originate the neighbor search.
     */
    public GraphBackedConstraintManager(FocalNodeGraph graph,
                                        StationPackingInstance instance,
                                        Station focalStation) {

        stationNeighborIndex = buildStationNeighborIndex(graph, instance, focalStation);;
    }

    /**
     * Given an abstract graph that we want to emulate with this constraint manager, build a NeighborIndex
     *  of stations that mirrors the relationships between the nodes of the abstract graph.
     * @param graph
     * @param instance
     * @param focalStation
     * @return - an index of neighboring stations that mirrors the configuration of nodes in the given graph.
     */
    private NeighborIndex<Station, DefaultEdge> buildStationNeighborIndex(FocalNodeGraph graph,
                                                                          StationPackingInstance instance,
                                                                          Station focalStation) {

        Map<Node,Station> nodeToStationMap = pairStationsToGraphNodes(graph, instance, focalStation);

        NeighborIndex<Node, DefaultEdge> nodeNeighborIndex = new NeighborIndex<>(graph);
        SimpleGraph<Station, DefaultEdge> stationGraph = new SimpleGraph<>(DefaultEdge.class);

        // Populate the station graph in a manner that mirrors the graph of nodes
        for(Node currentNode : graph.vertexSet()) {
            Station currentStation = nodeToStationMap.get(currentNode);
            stationGraph.addVertex(currentStation);

            for(Node nodeNeighbor : nodeNeighborIndex.neighborsOf(currentNode)) {
                Station stationNeighbor = nodeToStationMap.get(nodeNeighbor);
                stationGraph.addEdge(currentStation, stationNeighbor);
            }

        }

        return new NeighborIndex<>(stationGraph);
    }

    /**
     * Make a 1-to-1 mapping between each element in the graph, and each of the instance's stations.
     * @param graph
     * @param instance - the instance of a station packing problem telling us the stations in which we're interested;
     *                 IMPORTANT: Assumes that the number of stations in the instance is *at least* as many as the
     *                 number of nodes in the graph.
     * @param focalStation - the station from which we wish to originate the neighbor search.
     * @return - a map of nodes to stations.
     */
    private Map<Node, Station> pairStationsToGraphNodes(FocalNodeGraph graph, StationPackingInstance instance, Station focalStation) {

        assert (instance.getStations().size() >= graph.vertexSet().size());
        Map<Node, Station> nodeToStationMap = new HashMap<>();

        // Remove the focal station from the original set, since we add it separately
        Set<Station> stationSet = new HashSet<>(instance.getStations());
        stationSet.remove(focalStation);
        nodeToStationMap.put(graph.getFocalNode(), focalStation);

        // Turn the set of stations into a list so that we can iterate over it in parallel with the
        //  graph nodes. Note that the order of the stations is unimportant.
        // TODO: Implement this more elegantly
        List<Station> stations = new ArrayList<>(stationSet);
        int i = 0;
        for(Node node : graph.vertexSet()) {
            nodeToStationMap.put(node, stations.get(i));
            i++;
        }

        return nodeToStationMap;
    }

    /**
     * @param aStation - a (source) station of interest.
     * @param aChannel - any integer. This parameter is ignored (exists for compatibility with interface).
     * @return - a set of "conflicting stations" that is guaranteed to correspond to the shape of the graph with
     *  which this constraint manager was initialized.
     */
    @Override
    public Set<Station> getCOInterferingStations(Station aStation, int aChannel) {
        return this.stationNeighborIndex.neighborsOf(aStation);
    }

}
