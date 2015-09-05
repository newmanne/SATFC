package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ASolverBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.NativeLibraryGenerator;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.CompressedSATBasedSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.GenericSATBasedSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.base.SATSolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.NativeUtils;
import com.google.common.collect.ImmutableSet;
import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * Created by newmanne on 04/09/15.
 */
public interface DCCALibrary extends Library {

    void initProblem(String problem, long seed, double cutoff);

    void solveProblem();

    void destroyProblem();

    boolean interrupt();

    public static class DCCASolver extends AbstractCompressedSATSolver {

        @Override
        public SATSolverResult solve(CNF aCNF, ITerminationCriterion aTerminationCriterion, long aSeed) {
            DCCALibrary dccaLibrary = (DCCALibrary) Native.loadLibrary("/home/newmnanne/dcca.so", DCCALibrary.class, NativeUtils.NATIVE_OPTIONS);
            dccaLibrary.initProblem(aCNF.toDIMACS(null), aSeed, aTerminationCriterion.getRemainingTime());
            return new SATSolverResult(SATResult.TIMEOUT, 30.0, ImmutableSet.of());
        }

        @Override
        public void notifyShutdown() {

        }
    }

    public static class DCCABundle extends ASolverBundle {

        ISolver solver;

        /**
         * Create an abstract solver bundle with the given data management objects.
         *
         * @param aStationManager    - manages stations.
         * @param aConstraintManager - manages constraints.
         */
        public DCCABundle(IStationManager aStationManager, IConstraintManager aConstraintManager) {
            super(aStationManager, aConstraintManager);
            final SATCompressor aCompressor = new SATCompressor(this.getConstraintManager());
            solver = new CompressedSATBasedSolver(new DCCASolver(), aCompressor, getConstraintManager());
        }

        @Override
        public ISolver getSolver(StationPackingInstance aInstance) {
            return solver;
        }

        @Override
        public void close() throws Exception {

        }
    }

}
