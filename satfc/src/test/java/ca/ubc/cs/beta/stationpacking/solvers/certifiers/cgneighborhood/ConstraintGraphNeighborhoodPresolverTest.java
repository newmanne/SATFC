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
import ca.ubc.cs.beta.stationpacking.test.StationWholeSetSATCertifier;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author pcernek
 */
public class ConstraintGraphNeighborhoodPresolverTest {

    private static final int ARBITRARY_CHANNEL = 42;
    private static ITerminationCriterion mockTerminationCriterion;
    private static long arbitrarySeed;
//    private static List<IStationSubsetCertifier> neverSATCertifier = new ArrayList<>();

    SimpleGraph <Station,DefaultEdge> noNeighbors;
    SimpleGraph <Station,DefaultEdge> bigConnectedGraph;
    SimpleGraph <Station,DefaultEdge> oneRingOfNeighbors;
//    SimpleGraph <Station,DefaultEdge> hubAndSpoke;
//    SimpleGraph <Station,DefaultEdge> longChainOfNeighbors;
//    SimpleGraph <Station,DefaultEdge> shortChainOfNeighbors;
//    SimpleGraph <Station,DefaultEdge> biPartiteGraph;
//    SimpleGraph <Station,DefaultEdge> twoDisconnectedComponents;

    private static SimpleGraph<Station, DefaultEdge> emptyGraph = new SimpleGraph<>(DefaultEdge.class);

    @Before
    public void setUp() throws Exception {

        // This mock termination criterion is never met.
        mockTerminationCriterion = mock(ITerminationCriterion.class);
        when(mockTerminationCriterion.hasToStop()).thenReturn(false);

        // Completely random seed; value is not actually used since we never use any actual solvers here
        arbitrarySeed = 17;

        // This mock certifier never returns SAT.
//        IStationSubsetCertifier neverSAT = mock(IStationSubsetCertifier.class);
//        when(neverSAT.certify(any(), any(), mockTerminationCriterion, arbitrarySeed)).thenReturn(new SolverResult(SATResult.UNSAT, 0));
//        neverSATCertifier.add(neverSAT);

        // generate graphs from files
        noNeighbors                = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/noNeighbors.txt")).getStationGraph();
        bigConnectedGraph          = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/bigConnectedGraph.txt")).getStationGraph();
        oneRingOfNeighbors         = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/oneRingOfNeighbors.txt")).getStationGraph();
//        hubAndSpoke                = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/hubAndSpoke.txt")).getStationGraph();
//        longChainOfNeighbors       = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/longChainOfNeighbors.txt")).getStationGraph();
//        shortChainOfNeighbors      = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/shortChainOfNeighbors.txt")).getStationGraph();
//        biPartiteGraph             = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/biPartiteGraph.txt")).getStationGraph();
//        twoDisconnectedComponents  = new SimpleGraphBuilder(SATFCPaths.resourceLocationToPath("graphs/twoDisconnectedComponents.txt")).getStationGraph();

    }

    @Test
    public void testEmptyGraph() throws Exception {
    	// We expect solve to return immediately since the previous assignment will be empty.
    	testGraph(emptyGraph, emptyGraph, Collections.emptySet(), 0, SATResult.TIMEOUT);
    }
    
    @Test
    public void testSingletonGraph() throws Exception {
    	SimpleGraph<Station, DefaultEdge> singleton = new SimpleGraph<>(DefaultEdge.class);
    	singleton.addVertex(new Station(0));
    	// We expect to return immediately since the previous assignment will be empty.
    	testGraph(singleton, emptyGraph, Collections.singleton(new Station(0)), 0, SATResult.TIMEOUT);
    }
    
    @Test
	public void testNoNeighbors() throws Exception {
	    testGraph(noNeighbors, emptyGraph, Collections.singleton(new Station(0)), 1, SATResult.SAT);
	    HashSet<Station> startingStations = new HashSet<>(Arrays.asList(new Station(0), new Station(1)));
		testGraph(noNeighbors, emptyGraph, startingStations, 1, SATResult.SAT);
	}

	@Test
	public void testBigConnectedGraph() throws Exception {
	    testGraph(bigConnectedGraph, emptyGraph, Collections.singleton(new Station(0)), 4, SATResult.SAT);
	    HashSet<Station> startingStations = new HashSet<>(Arrays.asList(new Station(0), new Station(14)));
	    testGraph(bigConnectedGraph, emptyGraph, startingStations, 2, SATResult.SAT);
	}

	@Test
    public void testOneRingOfNeighbors() throws Exception {
        testGraph(oneRingOfNeighbors, emptyGraph, Collections.singleton(new Station(0)), 1, SATResult.SAT);
        testGraph(emptyGraph, oneRingOfNeighbors, Collections.singleton(new Station(0)), 1, SATResult.SAT);
    }
	
	@Test
	public void testSolve() throws Exception {
	
	}

	private StationPackingInstance initializeInstance(SimpleGraph<Station, DefaultEdge> coGraph, 
			SimpleGraph<Station, DefaultEdge> adjGraph, Set<Station> newStations) {
	    Set<Station> allStations = new HashSet<>(coGraph.vertexSet());
	    allStations.addAll(adjGraph.vertexSet());
	
	    Map<Station, Set<Integer>> domains = new HashMap<>();
	    Map<Station, Integer> previousAssignment = new HashMap<>();
	
	    /*
	     * Domains and previous assignment are set to two arbitrary but consecutive channels for all stations;
	     *  we don't particularly care about channels for this test, but the method ConstraintGrouper.getConstraintGraph
	     *  does require both channels to be in a station's domain to function properly.
	     */
	    for(Station station: allStations) {
	        domains.put(station, new HashSet<>(Arrays.asList(ARBITRARY_CHANNEL, ARBITRARY_CHANNEL + 1)));
	        if (!newStations.contains(station)) {
	        	previousAssignment.put(station, ARBITRARY_CHANNEL);
	        	previousAssignment.put(station, ARBITRARY_CHANNEL + 1);
	        }
	    }
	
	    return new StationPackingInstance(domains, previousAssignment);
	}

	private void testGraph(SimpleGraph<Station, DefaultEdge> coGraph, SimpleGraph<Station, DefaultEdge> adjGraph, 
			Set<Station> startingStations, int numberOfTimesToCall, SATResult expectedResult )
	{
		IConstraintManager constraintManager = new GraphBackedConstraintManager(coGraph, adjGraph);
        
        StationPackingInstance instance = initializeInstance(coGraph, adjGraph, startingStations);
        StationWholeSetSATCertifier certifier = new StationWholeSetSATCertifier(Arrays.asList(coGraph, adjGraph), startingStations);
        List<IStationSubsetCertifier> certifierList = Collections.singletonList(certifier);

        ConstraintGraphNeighborhoodPresolver presolver = new ConstraintGraphNeighborhoodPresolver(constraintManager, certifierList);
        SolverResult result = presolver.solve(instance, mockTerminationCriterion, arbitrarySeed);
        assertEquals(numberOfTimesToCall, certifier.getNumberOfTimesCalled());
        assertEquals(expectedResult, result.getResult());
	}
    
    @After
	public void tearDown() throws Exception {
	
	}
}