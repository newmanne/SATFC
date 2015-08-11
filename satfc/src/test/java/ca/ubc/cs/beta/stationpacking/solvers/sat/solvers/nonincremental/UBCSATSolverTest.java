package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.ChannelSpecificConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.DomainStationManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.Converter;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.UBCSATLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.Literal;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.ISATEncoder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATEncoder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.base.SATSolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.cputime.CPUTimeTerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.walltime.WalltimeTerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.io.Resources;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * @author pcernek
 */
public class UBCSATSolverTest {

    private static final String libraryPath = SATFCFacadeBuilder.findSATFCLibrary(SATFCFacadeBuilder.SATFCLibLocation.UBCSAT);

    private static SATEncoder.CNFEncodedProblem unsatProblem;
    private static SATEncoder.CNFEncodedProblem easyProblem;
    private static SATEncoder.CNFEncodedProblem moderateProblem;
    private static SATEncoder.CNFEncodedProblem otherProblem;

    private static String config1 = "-alg satenstein -adaptive 0 -alpha 1.3 -clausepen 0 -heuristic 5 -maxinc 10 -novnoise 0.3 -performrandomwalk 1 -pflat 0.15  -promisinglist 0 -randomwalk 4 -rfp 0.07 -rho 0.8 -sapsthresh -0.1 -scoringmeasure 1 -selectclause 1  -singleclause 1 -tabusearch 0  -varinfalse 1";
    private static String config2 = "-alg satenstein -adaptivenoisescheme 1 -adaptiveprom 0 -adaptpromwalkprob 0 -adaptwalkprob 0 -alpha 1.126 -decreasingvariable 3 -dp 0.05 -heuristic 2 -novnoise 0.5 -performalternatenovelty 1 -phi 5 -promdp 0.05 -promisinglist 0 -promnovnoise 0.5 -promphi 5 -promtheta 6 -promwp 0.01 -rho 0.17 -scoringmeasure 3 -selectclause 1 -theta 6 -tiebreaking 1 -updateschemepromlist 3 -wp 0.03 -wpwalk 0.3 -adaptive 1 -clausepen 1 -performrandomwalk 0 -singleclause 0 -smoothingscheme 1 -tabusearch 0 -varinfalse 1";
    private static String config3 = "-alg satenstein -adaptivenoisescheme 2 -adaptiveprom 0 -adaptpromwalkprob 0 -adaptwalkprob 0 -alpha 1.126 -c 0.0001 -decreasingvariable 3 -dp 0.05 -heuristic 2 -novnoise 0.5 -performalternatenovelty 1 -phi 5 -promdp 0.05 -promisinglist 0 -promnovnoise 0.5 -promphi 5 -promtheta 6 -promwp 0.01 -ps 0.033 -rho 0.8 -s 0.001 -scoringmeasure 3 -selectclause 1 -theta 6 -tiebreaking 3 -updateschemepromlist 3 -wp 0.04  -wpwalk 0.3 -adaptive 0 -clausepen 1 -performrandomwalk 0 -singleclause 0 -smoothingscheme 1 -tabusearch 0 -varinfalse 1";

    @BeforeClass
    public static void init() throws IOException {
        final IStationManager stationManager = new DomainStationManager(Resources.getResource("data/021814SC3M/Domain.csv").getFile());
        final IConstraintManager manager = new ChannelSpecificConstraintManager(stationManager, Resources.getResource("data/021814SC3M/Interference_Paired.csv").getFile());
        ISATEncoder aSATEncoder = new SATCompressor(manager);

        unsatProblem = parseSRPK(Resources.getResource("data/srpks/2469-2483_1671735211211766343_33.srpk").getFile(), aSATEncoder, stationManager);
        easyProblem = parseSRPK(Resources.getResource("data/srpks/2469-2483_1582973565573889317_33.srpk").getFile(), aSATEncoder, stationManager);
        moderateProblem = parseSRPK(Resources.getResource("data/srpks/2469-2483_4310537143272356051_107.srpk").getFile(), aSATEncoder, stationManager);
        otherProblem = parseSRPK(Resources.getResource("data/srpks/2469-2483_1853993831659981677_29.srpk").getFile(), aSATEncoder, stationManager);

    }

    @Test
    public void testSolveEasy() throws Exception {
        UBCSATSolver solver = new UBCSATSolver(libraryPath, UBCSATLibSATSolverParameters.DEFAULT_SATENSTEIN);
        SATEncoder.CNFEncodedProblem currentProblem = easyProblem;
        ITerminationCriterion terminationCriterion = new CPUTimeTerminationCriterion(1.0);
        SATSolverResult result = solver.solve(currentProblem.getCnf(), currentProblem.getInitialAssignment(), terminationCriterion, 1);

        assertEquals(SATResult.SAT, result.getResult());
        checkSolutionMakesSense(result, currentProblem.getCnf().getVariables().size());
    }

    @Test
    public void testTimeout() throws Exception {
        UBCSATSolver solver = new UBCSATSolver(libraryPath, UBCSATLibSATSolverParameters.DEFAULT_SATENSTEIN);

//        Watch watch = Watch.constructAutoStartWatch();

        ITerminationCriterion terminationCriterion = new WalltimeTerminationCriterion(0.5);
        SATSolverResult result = solver.solve(unsatProblem.getCnf(), unsatProblem.getInitialAssignment(), terminationCriterion, 1);
//        double solvingTime = watch.getElapsedTime();
//        System.out.println("Solving time: " + solvingTime);
//        watch.stop();

        assertEquals(SATResult.TIMEOUT, result.getResult());
//        assertTrue(solvingTime < 1);
    }

    @Test
    public void testSolveMultipleInstances() throws Exception {
        UBCSATSolver solver = new UBCSATSolver(libraryPath, UBCSATLibSATSolverParameters.DEFAULT_SATENSTEIN);

        // Problem 1
        SATEncoder.CNFEncodedProblem currentProblem = easyProblem;
        ITerminationCriterion terminationCriterion = new CPUTimeTerminationCriterion(10.0);
        SATSolverResult result = solver.solve(currentProblem.getCnf(), currentProblem.getInitialAssignment(), terminationCriterion, 1);
        assertEquals(SATResult.SAT, result.getResult());
        checkSolutionMakesSense(result, currentProblem.getCnf().getVariables().size());

        // Problem 2
        currentProblem = moderateProblem;
        terminationCriterion = new CPUTimeTerminationCriterion(10.0);
        result = solver.solve(currentProblem.getCnf(), currentProblem.getInitialAssignment(), terminationCriterion, 1);
        assertEquals(SATResult.SAT, result.getResult());
        checkSolutionMakesSense(result, currentProblem.getCnf().getVariables().size());

        // Problem 3
//        currentProblem = otherProblem;
//        terminationCriterion = new CPUTimeTerminationCriterion(10.0);
//        result = solver.solve(currentProblem.getCnf(), currentProblem.getInitialAssignment(), terminationCriterion, 1);
//        assertEquals(SATResult.SAT, result.getResult());
//        checkSolutionMakesSense(result, currentProblem.getCnf().getVariables().size());

    }

    @Test
    public void testSolveMultipleConfigsSameInstance() {
        SATEncoder.CNFEncodedProblem currentProblem = easyProblem;
        ITerminationCriterion terminationCriterion;
        SATSolverResult result;

        UBCSATSolver solver = new UBCSATSolver(libraryPath, config1);
        terminationCriterion = new CPUTimeTerminationCriterion(1.0);
        result = solver.solve(currentProblem.getCnf(), currentProblem.getInitialAssignment(), terminationCriterion, 1);
        assertEquals(SATResult.SAT, result.getResult());
        checkSolutionMakesSense(result, currentProblem.getCnf().getVariables().size());

        solver = new UBCSATSolver(libraryPath, config2);
        terminationCriterion = new CPUTimeTerminationCriterion(1.0);
        result = solver.solve(currentProblem.getCnf(), currentProblem.getInitialAssignment(), terminationCriterion, 1);
        assertEquals(SATResult.SAT, result.getResult());
        checkSolutionMakesSense(result, currentProblem.getCnf().getVariables().size());

        solver = new UBCSATSolver(libraryPath, config3);
        terminationCriterion = new CPUTimeTerminationCriterion(1.0);
        result = solver.solve(currentProblem.getCnf(), currentProblem.getInitialAssignment(), terminationCriterion, 1);
        assertEquals(SATResult.SAT, result.getResult());
        checkSolutionMakesSense(result, currentProblem.getCnf().getVariables().size());
    }

    @Test @Ignore
    public void testSameProblemHundredTimes() {
//        SATEncoder.CNFEncodedProblem currentProblem = otherProblem;
        SATEncoder.CNFEncodedProblem currentProblem = easyProblem;

        ITerminationCriterion terminationCriterion;
        UBCSATSolver solver;
        SATSolverResult result;

        for (int i=0; i < 100; i++) {
            solver = new UBCSATSolver(libraryPath, UBCSATLibSATSolverParameters.DEFAULT_SATENSTEIN);
//            solver = new UBCSATSolver(libraryPath, config3);
            terminationCriterion = new CPUTimeTerminationCriterion(1.0);
            result = solver.solve(currentProblem.getCnf(), currentProblem.getInitialAssignment(), terminationCriterion, 1);
            assertEquals(SATResult.SAT, result.getResult());
            checkSolutionMakesSense(result, currentProblem.getCnf().getVariables().size());
        }
    }

    @Test
    public void testInterrupt() throws Exception {
        UBCSATSolver solver = new UBCSATSolver(libraryPath, UBCSATLibSATSolverParameters.DEFAULT_SATENSTEIN);

        Thread interrupter = new Thread(() -> {
            try {
                Thread.sleep(200);
                solver.interrupt();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        Watch watch = new Watch();
        watch.start();

        ITerminationCriterion terminationCriterion = new CPUTimeTerminationCriterion(5);
        interrupter.start();
        SATSolverResult result = solver.solve(unsatProblem.getCnf(), unsatProblem.getInitialAssignment(), terminationCriterion, 1);

        watch.stop();
        interrupter.join();
        assertEquals(SATResult.INTERRUPTED, result.getResult());
        assertTrue(watch.getElapsedTime() < 0.5);
    }

    private static SATEncoder.CNFEncodedProblem parseSRPK(String srpkFilePath, ISATEncoder encoder, IStationManager stationManager) throws IOException {
        Converter.StationPackingProblemSpecs problemSpecs = Converter.StationPackingProblemSpecs.fromStationRepackingInstance(srpkFilePath);
        StationPackingInstance instance = new StationPackingInstance(problemSpecs.getDomains().entrySet().stream().collect(Collectors.toMap(e -> stationManager.getStationfromID(e.getKey()), Map.Entry::getValue)), problemSpecs.getPreviousAssignment().entrySet().stream().collect(Collectors.toMap(e -> stationManager.getStationfromID(e.getKey()), Map.Entry::getValue)));
        return encoder.encodeWithAssignment(instance);
    }

    private static void checkSolutionMakesSense(SATSolverResult result, int numVars) {
        assertEquals(numVars, result.getAssignment().size());

        for (int i=1; i <= numVars; i++) {
            boolean containsPositiveLit = result.getAssignment().contains(new Literal(i, true));
            boolean containsNegativeLit = result.getAssignment().contains(new Literal(i, false));
            assertTrue( containsPositiveLit != containsNegativeLit );
        }
    }
}