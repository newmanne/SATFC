package ca.ubc.cs.beta.stationpacking.test;

import org.jgrapht.EdgeFactory;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

/**
 * Created by pcernek on 4/29/15.
 */
public class FocalNodeGraph extends SimpleGraph<Node, DefaultEdge> implements Graph<Node, DefaultEdge> {
    private final Node focalNode;

    public FocalNodeGraph(Node focalNode) {
        super(DefaultEdge.class);
        this.focalNode = focalNode;
    }

    public Node getFocalNode() {
        return this.focalNode;
    }

}
