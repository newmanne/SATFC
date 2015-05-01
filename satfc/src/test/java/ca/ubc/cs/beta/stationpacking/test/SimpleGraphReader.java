package ca.ubc.cs.beta.stationpacking.test;

import ca.ubc.cs.beta.stationpacking.base.Station;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.io.File;

/**
 * <p>
 * Reads a text file that represents a simple graph, where each line in the file represents an edge
 *  between two nodes, and each node is designated by an integer. Nodes are separated by a space.
 *  Nodes are interpreted to represent stations; each numerical node is used to construct a Station,
 *  where Stations are defined completely by a single integer.
 * </p>
 *
 * <p>
 *     Note that in certain situations, one station needs to be designated as a starting point in a search.
 *     For such cases, node number 0 is reserved to represent this node.
 * </p>
 *
 * <p>
 *  Example contents of a simple graph file:
 *  1 0
 *  1 2
 *  0 3
 * </p>
 *
 * <p>
 *     This example graph file represents a graph consisting of stations 0, 1, 2, and 3, with edges between
 *     stations 1 and 0, 1 and 2, and 0 and 3.
 * </p>
 *
 * @author pcernek
 */
public class SimpleGraphReader implements IStationGraphReader {

    private final SimpleGraph<Station, DefaultEdge> stationGraph;

    /**
     * Constructs a SimpleGraphReader that reads the given file and builds a graph from it.
     * @param graphFileRelativePath - path to the graph file, relative to the project's resources directory.
     */
    public SimpleGraphReader(String graphFileRelativePath) {
        File graphFile = new File(graphFileRelativePath);
        stationGraph = parseGraphFile(graphFile);
    }

    /**
     * Parse the given graph file and produce a simple graph.
     * @param graphFile - the file containing a representation of a simple undirected graph.
     * @return - the simple graph.
     */
    private SimpleGraph<Station, DefaultEdge> parseGraphFile(File graphFile) {
        return null;
    }

    @Override
    public SimpleGraph<Station, DefaultEdge> getStationGraph() {
        return this.stationGraph;
    }
}
