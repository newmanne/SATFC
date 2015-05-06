package ca.ubc.cs.beta.stationpacking.datamanagers.constraints;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.test.GraphLoader;

public class GraphBackedConstraintManagerTest {
	
	GraphLoader graphLoader;
	
	@Before
	public void setUp() throws Exception {
		graphLoader = new GraphLoader();
		graphLoader.loadAllGraphs();
	}

	@Test
	public void testGetCOInterferingStations() throws Exception {
		
	}
	
	@Test
	public void testGetADJInterferingStations() throws Exception {
		
	}
	
	@Test
	public void testNeighborsOnSameChannel() throws Exception {
		GraphBackedConstraintManager cm = new GraphBackedConstraintManager(graphLoader.getHubAndSpoke(), graphLoader.getEmptyGraph());
		Map<Integer, Set<Station>> assignment = new HashMap<>();
		assignment.put(0, Collections.singleton(new Station(0)));
		assert(cm.isSatisfyingAssignment(assignment));
	}
	
	@Test
	public void testIsSatisfyingAssignment() {
		fail("Not yet implemented"); // TODO
	}
	
}
