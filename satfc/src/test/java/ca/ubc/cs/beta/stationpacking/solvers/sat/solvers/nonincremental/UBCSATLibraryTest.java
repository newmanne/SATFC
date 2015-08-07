package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.ChannelSpecificConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.DomainStationManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.Converter;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.ISATDecoder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.ISATEncoder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.UBCSATTestHelperLibrary;
import ca.ubc.cs.beta.stationpacking.utils.NativeUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.io.Resources;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.math3.util.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by pcernek on 7/28/15.
 */
public class UBCSATLibraryTest {

    private UBCSATTestHelperLibrary library;
    private Pointer state;

    String defaultParameters = "-alg sparrow -cutoff 100000000";

    String dummyCNF = "p cnf 4 20\n-1 2 3 0\n1 -2 4 0\n1 3 4 0\n2 3 -4 0\n-1 -2 -4 0\n1 -2 3 0\n2 -3 4 0\n-2 3 4 0\n-1 3 -4 0\n2 3 4 0\n1 2 -3 0\n1 -3 -4 0\n1 3 -4 0\n-1 2 4 0\n-1 -2 -3 0\n1 2 0\n-2 4 0\n-3 4 0\n2 3 0\n1 2 3 4 0\n";
    String miniUnsatCNF = "p cnf 4 8\n-1 2 4 0\n-2 3 4 0\n1 -3 4 0\n1 -2 -4 0\n2 -3 -4 0\n-1 3 -4 0\n1 2 3 0\n-1 -2 -3 0";

    String sampleSRPKPath = Resources.getResource("data/srpks/2469-2483_1582973565573889317_33.srpk").getFile();

    private CNF loadSRPKasCNF(String srpkPath) throws IOException {
        final IStationManager stationManager = new DomainStationManager(Resources.getResource("data/021814SC3M/Domain.csv").getFile());
        final IConstraintManager manager = new ChannelSpecificConstraintManager(stationManager, Resources.getResource("data/021814SC3M/Interference_Paired.csv").getFile());
        Converter.StationPackingProblemSpecs specs = Converter.StationPackingProblemSpecs.fromStationRepackingInstance(srpkPath);
        final StationPackingInstance instance = new StationPackingInstance(specs.getDomains().entrySet().stream().collect(Collectors.toMap(e -> stationManager.getStationfromID(e.getKey()), e -> e.getValue())), specs.getPreviousAssignment().entrySet().stream().collect(Collectors.toMap(e -> stationManager.getStationfromID(e.getKey()), e -> e.getValue())));
        ISATEncoder aSATEncoder = new SATCompressor(manager);
        Pair<CNF, ISATDecoder> aEncoding = aSATEncoder.encode(instance);
        return aEncoding.getKey();
    }

    private String loadCNF(String cnfPath) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(cnfPath));
        return new String(encoded);
    }

    @Before
    public void setup() {
        // TODO: Replace hard-coded path with SATFCFacadeBuilder.findSATFCLibrary(SATFCFacadeBuilder.SATFCLibLocation.UBCSAT)
        String libraryPath = "/ubc/cs/project/arrow/pcernek/code/SATenstein/build/libjnaubcsat.so";
        library = (UBCSATTestHelperLibrary) Native.loadLibrary(libraryPath, UBCSATTestHelperLibrary.class, NativeUtils.NATIVE_OPTIONS);
        state = library.initConfig(defaultParameters);
    }

    @Test
    public void testInitProblem() {
        library.initProblem(state, dummyCNF);

        assertEquals(4, library.getNumVars());
        assertEquals(20, library.getNumClauses());
    }

    @Test
    public void testInitAssignment() {
        assertTrue( library.initProblem(state, dummyCNF) );

        long[] assignment = new long[]{1, 2, -3};

        assertTrue( library.initAssignment(state, assignment, 3) );
        library.runInitData();

        for(int i=0; i < assignment.length; i++) {
            assertEquals(assignment[i] > 0, library.getVarAssignment(i + 1));
        }
    }

    @Test
    public void testSolveProblem() throws IOException {
        assertTrue(library.initProblem(state, dummyCNF));
        assertTrue(library.solveProblem(state, 0.5));

        assertEquals(1, library.getResultState(state));

        IntByReference pRef = library.getResultAssignment(state);
        int size = pRef.getValue();
        int[] assignment = pRef.getPointer().getIntArray(0, size + 1);

        assertEquals(true, assignment[1] > 0);
        assertEquals(false, assignment[2] > 0);
        assertEquals(true, assignment[3] > 0);
        assertEquals(true, assignment[4] > 0);
    }

    @Test
    public void testSolveTwoDifferentProblems() throws IOException {
//        String facCNF = loadCNF(facCNFPath);
//        library.initProblem(state, facCNF);
        CNF sampleSRPK = loadSRPKasCNF(sampleSRPKPath);
        assertTrue( library.initProblem(state, sampleSRPK.toDIMACS(new String[]{})) );
        assertTrue( library.solveProblem(state, 10) );
        assertEquals(1, library.getResultState(state));

        library.destroyProblem(state);

        state = library.initConfig(defaultParameters);
        assertTrue( library.initProblem(state, dummyCNF) );
        assertTrue( library.solveProblem(state, 5) );
        assertEquals(1, library.getResultState(state));
    }

    @Test
    public void testSameProblemDifferentConfigs() {
        library.destroyProblem(state);

        String params1 = "-alg satenstein -adaptive 0 -alpha 1.3 -clausepen 0 -heuristic 5 -maxinc 10 -novnoise 0.3 -performrandomwalk 1 -pflat 0.15  -promisinglist 0 -randomwalk 4 -rfp 0.07 -rho 0.8 -sapsthresh -0.1 -scoringmeasure 1 -selectclause 1  -singleclause 1 -tabusearch 0  -varinfalse 1";
        state = library.initConfig(params1);
        assertTrue(library.initProblem(state, dummyCNF));
        assertTrue(library.solveProblem(state, 0.5));
        assertEquals(1, library.getResultState(state));
        library.destroyProblem(state);

        String params2 = "-alg satenstein -adaptivenoisescheme 1 -adaptiveprom 0 -adaptpromwalkprob 0 -adaptwalkprob 0 -alpha 1.126 -decreasingvariable 3 -dp 0.05 -heuristic 2 -novnoise 0.5 -performalternatenovelty 1 -phi 5 -promdp 0.05 -promisinglist 0 -promnovnoise 0.5 -promphi 5 -promtheta 6 -promwp 0.01 -rho 0.17 -scoringmeasure 3 -selectclause 1 -theta 6 -tiebreaking 1 -updateschemepromlist 3 -wp 0.03 -wpwalk 0.3 -adaptive 1 -clausepen 1 -performrandomwalk 0 -singleclause 0 -smoothingscheme 1 -tabusearch 0 -varinfalse 1";
        state = library.initConfig(params1);
        assertTrue(library.initProblem(state, dummyCNF));
        assertTrue(library.solveProblem(state, 0.5));
        assertEquals(1, library.getResultState(state));
        library.destroyProblem(state);

        String params3 = "-alg satenstein -adaptivenoisescheme 2 -adaptiveprom 0 -adaptpromwalkprob 0 -adaptwalkprob 0 -alpha 1.126 -c 0.0001 -decreasingvariable 3 -dp 0.05 -heuristic 2 -novnoise 0.5 -performalternatenovelty 1 -phi 5 -promdp 0.05 -promisinglist 0 -promnovnoise 0.5 -promphi 5 -promtheta 6 -promwp 0.01 -ps 0.033 -rho 0.8 -s 0.001 -scoringmeasure 3 -selectclause 1 -theta 6 -tiebreaking 3 -updateschemepromlist 3 -wp 0.04  -wpwalk 0.3 -adaptive 0 -clausepen 1 -performrandomwalk 0 -singleclause 0 -smoothingscheme 1 -tabusearch 0 -varinfalse 1";
        state = library.initConfig(params1);
        assertTrue(library.initProblem(state, dummyCNF));
        assertTrue(library.solveProblem(state, 0.5));
        assertEquals(1, library.getResultState(state));
    }

    @Test
    public void testInterrupt() throws IOException {
        Watch watch = new Watch();
        watch.start();

        library.initProblem(state, miniUnsatCNF);

        Thread interrupter = new Thread(() -> {
            try {
                Thread.sleep(200);
                library.interrupt(state);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        interrupter.start();

        library.solveProblem(state, 3);

        watch.stop();

        assertTrue(watch.getElapsedTime() < 2);
        assertEquals(3, library.getResultState(state));
    }

    @Test
    public void testTimeout() throws IOException {
        StopWatch watch = new StopWatch();
        watch.start();

        library.initProblem(state, miniUnsatCNF);
        library.solveProblem(state, 0.5);

        watch.stop();

        assertTrue(watch.getTime() < 1000);
        assertEquals(2, library.getResultState(state));
    }

    @After
    public void cleanup() {
        library.destroyProblem(state);
    }


}