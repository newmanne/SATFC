package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.junit.BeforeClass;
import org.junit.Test;

import ca.ubc.cs.beta.stationpacking.StationPackingTestUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.PythonInterpreterFactory;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;

/**
 * Created by emily404 on 9/3/15.
 */
@Slf4j
public class PythonAssignmentVerifierDecoratorTest {

    final static ISolver solver = mock(ISolver.class);
    static PythonAssignmentVerifierDecorator pythonAssignmentVerifierDecorator;

    @BeforeClass
    public static void setUp() {
        final String interferenceFolder = Resources.getResource("data/021814SC3M").getPath();
        final boolean compact = true;
        pythonAssignmentVerifierDecorator = new PythonAssignmentVerifierDecorator(solver, new PythonInterpreterFactory(interferenceFolder, compact));
    }

    @Test(expected=IllegalStateException.class)
    public void testDomainViolation() {

        final StationPackingInstance instance = new StationPackingInstance(Maps.newHashMap());
        final ITerminationCriterion terminationCriterion = mock(ITerminationCriterion.class);
        final long seed = 0;

        Map<Integer,Set<Station>> domainViolationAnswer = ImmutableMap.of(-1, ImmutableSet.of(new Station(1)));
        when(solver.solve(any(StationPackingInstance.class), eq(terminationCriterion), eq(seed)))
                .thenReturn(new SolverResult(SATResult.SAT, 0, domainViolationAnswer, SolverResult.SolvedBy.UNKNOWN));

        pythonAssignmentVerifierDecorator.solve(instance, terminationCriterion, seed);

    }


    @Test
    public void testNoViolation() {

        final StationPackingInstance instance = new StationPackingInstance(Maps.newHashMap());
        final ITerminationCriterion terminationCriterion = mock(ITerminationCriterion.class);
        final long seed = 0;

        when(solver.solve(any(StationPackingInstance.class), eq(terminationCriterion), eq(seed)))
                .thenReturn(new SolverResult(SATResult.SAT, 0, StationPackingTestUtils.getSimpleInstanceAnswer(), SolverResult.SolvedBy.UNKNOWN));

        SolverResult result = pythonAssignmentVerifierDecorator.solve(instance, terminationCriterion, seed);
        assertEquals(result.getAssignment(), StationPackingTestUtils.getSimpleInstanceAnswer());

    }

}
