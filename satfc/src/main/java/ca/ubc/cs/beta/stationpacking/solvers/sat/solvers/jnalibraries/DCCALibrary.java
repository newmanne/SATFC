package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ASolverBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.NativeLibraryGenerator;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.AssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.sat.CompressedSATBasedSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.GenericSATBasedSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.Literal;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.base.SATSolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.ClaspResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.NativeUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.collect.ImmutableSet;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Map;

/**
 * Created by newmanne on 04/09/15.
 */
public interface DCCALibrary extends Library {

    void initProblem(String problem, long seed);

    IntByReference solveProblem(long[] assignment, long assignmentSize, double cutoff);

    void destroyProblem();

    boolean interrupt();

    @Slf4j
    public static class DCCASolver extends AbstractCompressedSATSolver {

        @Override
        public SATSolverResult solve(CNF aCNF, Map<Long, Boolean> aPreviousAssignment, ITerminationCriterion aTerminationCriterion, long aSeed) {
            final Watch watch = Watch.constructAutoStartWatch();
            DCCALibrary dccaLibrary = (DCCALibrary) Native.loadLibrary("/home/newmanne/research/satfc/satfc/src/dist/dcca/DCCASat", DCCALibrary.class, NativeUtils.NATIVE_OPTIONS);
            dccaLibrary.initProblem(aCNF.toDIMACS(null), aSeed);
            long[] previousAssignmentArray = aPreviousAssignment.entrySet().stream().mapToLong(entry -> entry.getKey() * (entry.getValue() ? 1 : -1)).toArray();
            double cutoff = aTerminationCriterion.getRemainingTime();
            log.info("Sending cutoff of {} s", cutoff);
            if (cutoff > 0) {
                final IntByReference intByReference = dccaLibrary.solveProblem(previousAssignmentArray, previousAssignmentArray.length, cutoff);
                final HashSet<Literal> literals = parseAssignment(intByReference);
                final SATSolverResult output = new SATSolverResult(SATResult.SAT, watch.getElapsedTime(), literals);
                return output;
            }
            return new SATSolverResult(SATResult.TIMEOUT, 30.0, ImmutableSet.of());
        }

        @Override
        public SATSolverResult solve(CNF aCNF, ITerminationCriterion aTerminationCriterion, long aSeed) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void notifyShutdown() {

        }

        private HashSet<Literal> parseAssignment(IntByReference assignmentReference) {
            int size = assignmentReference.getValue();
            int[] assignment = assignmentReference.getPointer().getIntArray(0, size);
            HashSet<Literal> set = new HashSet<>();
            for (int i = 1; i < size; i++) {
                int var = i;
                boolean sign = assignment[i] > 0;
                Literal aLit = new Literal(var, sign);
                set.add(aLit);
            }
            return set;
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
            solver = new AssignmentVerifierDecorator(solver, getConstraintManager());
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
