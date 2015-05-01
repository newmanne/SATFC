package ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Created by pcernek on 4/28/15.
 */
public class ConstraintGraphNeighborhoodPresolverTest {

    StationPackingInstance gStationPackingInstance;
    ITerminationCriterion gTerminationCriterion;
    long gSeed;

    @Before
    public void setUp() throws Exception {
        gStationPackingInstance = mock(StationPackingInstance.class);
        gTerminationCriterion = mock(ITerminationCriterion.class);
        gSeed = 17;
        // generate graphs from files

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testSolve() throws Exception {

    }
}