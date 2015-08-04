package ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.Set;

/**
* Created by newmanne on 27/07/15.
*/
public interface IStationAddingStrategy {

    Iterable<Set<Station>> getStationsToPack(SimpleGraph<Station,DefaultEdge> graph, Set<Station> missingStations);

}
