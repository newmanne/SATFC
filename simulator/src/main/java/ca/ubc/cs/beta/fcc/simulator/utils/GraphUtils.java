package ca.ubc.cs.beta.fcc.simulator.utils;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import com.google.common.collect.ImmutableSet;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by newmanne on 2016-07-26.
 */
public class GraphUtils {

    public static Set<IStationInfo> getNeighboursAndSelf(IStationInfo station, SimpleGraph<IStationInfo, DefaultEdge> graph) {
        Set<IStationInfo> neighbours = new HashSet<>();
        graph.edgesOf(station).forEach(edge -> {
            neighbours.add((IStationInfo) graph.getEdgeSource(edge));
            neighbours.add((IStationInfo) graph.getEdgeTarget(edge));
        });
        return ImmutableSet.copyOf(neighbours);
    }

}
