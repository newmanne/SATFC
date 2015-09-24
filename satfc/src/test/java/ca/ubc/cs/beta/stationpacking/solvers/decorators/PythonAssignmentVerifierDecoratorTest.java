package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.StationPackingTestUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ASolverBundle;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Test;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

import java.io.File;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;

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
        pythonAssignmentVerifierDecorator = new PythonAssignmentVerifierDecorator(solver, getPythonInterpreter(interferenceFolder, compact));
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

    private static PythonInterpreter getPythonInterpreter(String interferenceFolder, boolean compact){

        PythonInterpreter python;

        python = new PythonInterpreter();
        python.execfile(PythonAssignmentVerifierDecoratorTest.class.getClassLoader().getResourceAsStream("verifier.py"));
        String interference = interferenceFolder + File.separator + DataManager.INTERFERENCES_FILE;
        String domain = interferenceFolder + "/" + DataManager.DOMAIN_FILE;

        String interferenceResult;
        if(compact){
            final String compactEvalString = "load_compact_interference(\"" + interference + "\")";
            log.info(compactEvalString);

            final PyObject compactReturnObject = python.eval(compactEvalString);
            interferenceResult = compactReturnObject.toString();
        }else{
            final String nonCompactEvalString = "load_interference(\"" + interference + "\")";
            log.info(nonCompactEvalString);

            final PyObject nonCompactReturnObject = python.eval(nonCompactEvalString);
            interferenceResult = nonCompactReturnObject.toString();
        }
        final String domainEvalString = "load_domain_csv(\"" + domain + "\")";
        final PyObject loadDomainReturnObject = python.eval(domainEvalString);
        final String domainResult = loadDomainReturnObject.toString();

        if (interferenceResult.equals("0") && domainResult.equals("0")){
            log.debug("Interference loaded");
        } else {
            throw new IllegalStateException("Interference not loaded properly");
        }

        return python;
    }

}
