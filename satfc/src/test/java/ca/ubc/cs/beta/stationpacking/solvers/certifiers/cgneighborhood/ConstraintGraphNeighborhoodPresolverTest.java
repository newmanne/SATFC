package ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.test.GraphBackedConstraintManager;
import ca.ubc.cs.beta.stationpacking.test.SATFCPaths;
import ca.ubc.cs.beta.stationpacking.test.SimpleGraphBuilder;
import ca.ubc.cs.beta.stationpacking.test.SupersetSATCertifier;
import com.google.common.io.Resources;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * @author pcernek
 */
public class ConstraintGraphNeighborhoodPresolverTest {

    private static final int ARBITRARY_CHANNEL = 42;
    private static ITerminationCriterion mockTerminationCriterion;
    private static long arbitrarySeed;
    private static List<IStationSubsetCertifier> alwaysSATCertifier = new ArrayList<>();
    private static List<IStationSubsetCertifier> neverSATCertifier = new ArrayList<>();

    SimpleGraph <Station,DefaultEdge> noNeighbors;
    SimpleGraph <Station,DefaultEdge> bigConnectedGraph;
    SimpleGraph <Station,DefaultEdge> oneRingOfNeighbors;
    SimpleGraph <Station,DefaultEdge> hubAndSpoke;
    SimpleGraph <Station,DefaultEdge> longChainOfNeighbors;
    SimpleGraph <Station,DefaultEdge> shortChainOfNeighbors;
    SimpleGraph <Station,DefaultEdge> biPartiteGraph;
    SimpleGraph <Station,DefaultEdge> twoDisconnectedComponents;

    private static SimpleGraph<Station, DefaultEdge> emptyGraph = new SimpleGraph<>(DefaultEdge.class);

    @Before
    public void setUp() throws Exception {

        // This mock termination criterion is never met.
        mockTerminationCriterion = mock(ITerminationCriterion.class);
        when(mockTerminationCriterion.hasToStop()).thenReturn(false);

        // Completely random seed; value is not actually used since we never use any actual solvers here
        arbitrarySeed = 17;

        // This mock certifier never returns SAT.
        IStationSubsetCertifier neverSAT = mock(IStationSubsetCertifier.class);
        when(neverSAT.certify(any(), any(), mockTerminationCriterion, arbitrarySeed)).thenReturn(new SolverResult(SATResult.UNSAT, 0));
        neverSATCertifier.add(neverSAT);

        alwaysSATCertifier.add(new SupersetSATCertifier());

        // generate graphs from files
        noNeighbors                = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/noNeighbors.txt")).getStationGraph();
        bigConnectedGraph          = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/bigConnectedGraph.txt")).getStationGraph();
        oneRingOfNeighbors         = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/oneRingOfNeighbors.txt")).getStationGraph();
        hubAndSpoke                = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/hubAndSpoke.txt")).getStationGraph();
        longChainOfNeighbors       = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/longChainOfNeighbors.txt")).getStationGraph();
        shortChainOfNeighbors      = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/shortChainOfNeighbors.txt")).getStationGraph();
        biPartiteGraph             = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/biPartiteGraph.txt")).getStationGraph();
        twoDisconnectedComponents  = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/twoDisconnectedComponents.txt")).getStationGraph();

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testNoNeighbors() throws Exception {
        IConstraintManager constraintManager = new GraphBackedConstraintManager(noNeighbors, emptyGraph);

        StationPackingInstance instance = initializeInstance(noNeighbors);

        // Ensure that the maximum number of layers of neighbors does not exceed 1
        ConstraintGraphNeighborhoodPresolver presolver = new ConstraintGraphNeighborhoodPresolver(constraintManager, neverSATCertifier);
        presolver.solve(instance, mockTerminationCriterion, arbitrarySeed);

    }

    private StationPackingInstance initializeInstance(SimpleGraph<Station, DefaultEdge> baseGraph) {
        Set<Station> allStations = baseGraph.vertexSet();
        Map<Station, Set<Integer>> currentDomains = new HashMap<>();
        Map<Station, Integer> previousAssignment = new HashMap<>();
        for(Station station: allStations) {
            currentDomains.put(station, new HashSet<Integer>(Arrays.asList(ARBITRARY_CHANNEL)));
            previousAssignment.put(station, ARBITRARY_CHANNEL);
        }
        // Remove the focal station from the previous assignment.
        previousAssignment.remove(new Station(0));

        return new StationPackingInstance(currentDomains, previousAssignment);
    }

    @Test
    public void testSolve() throws Exception {

    }
}