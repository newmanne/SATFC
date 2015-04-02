package ca.ubc.cs.beta.stationpacking;

import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.ClaspLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.ISATDecoder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.ISATEncoder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.ClaspLibrary;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;
import org.junit.Test;

/**
 * Created by newmanne on 01/04/15.
 */
@Slf4j
public class TestNative {

    // IntByReference
    public interface ClaspLibrary2 extends Library {
        void doThing(String params, String problem);
    }

    @Test
    public void test() {
        String libraryPath = "/home/newmanne/research/satfc/satfc/src/dist/clasp/libjnaclasp-3/jna/libjnaclasp.so";
        // load the library
        final ClaspLibrary2 claspLibrary2 = (ClaspLibrary2) Native.loadLibrary(libraryPath, ClaspLibrary2.class);

//        ISATEncoder fSATEncoder = new SATCompressor;
//        Pair<CNF, ISATDecoder> aEncoding = fSATEncoder.encode(aInstance);
//        CNF aCNF = aEncoding.getKey();
        String cnf = "c\n" +
                "p cnf 3 2\n" +
                "1 -3 0\n" +
                "2 3 -1 0\n";
//
//        cnf = "p cnf 4 8\n" +
//                " 1  2 -3 0\n" +
//                "-1 -2  3 0\n" +
//                " 2  3 -4 0\n" +
//                "-2 -3  4 0\n" +
//                " 1  3  4 0\n" +
//                "-1 -3 -4 0\n" +
//                "-1  2  4 0\n" +
//                " 1 -2 -4 0";
        // check if the configuration is valid.
        claspLibrary2.doThing(ClaspLibSATSolverParameters.ALL_CONFIG_11_13, cnf);
        log.error("YAYYAYAY");
    }
}
