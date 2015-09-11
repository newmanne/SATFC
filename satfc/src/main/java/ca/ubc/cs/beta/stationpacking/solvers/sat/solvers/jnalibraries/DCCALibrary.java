package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.ASolverBundle;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult.SolvedBy;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.AssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.sat.CompressedSATBasedSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.Literal;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.base.SATSolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.NativeUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

import com.google.common.collect.ImmutableSet;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;

/**
 * Created by newmanne on 04/09/15.
 */
public interface DCCALibrary extends Library {

    void initProblem(String problem, long seed);

    IntByReference solveProblem(long[] assignment, long assignmentSize, double cutoff);

    void destroyProblem(IntByReference assignmentPointer);

    boolean interrupt();

    @Slf4j
    public static class DCCASolver extends AbstractCompressedSATSolver {

        private final DCCALibrary dccaLibrary;
        private final AtomicBoolean isCurrentlySolving = new AtomicBoolean(false);
        private final Lock lock = new ReentrantLock();

        public DCCASolver() {
            dccaLibrary = (DCCALibrary) Native.loadLibrary("/home/newmanne/research/satfc/satfc/src/dist/dcca/DCCASat", DCCALibrary.class, NativeUtils.NATIVE_OPTIONS);
        }

        @Override
        public SATSolverResult solve(CNF aCNF, Map<Long, Boolean> aPreviousAssignment, ITerminationCriterion aTerminationCriterion, long aSeed) {
            final Watch watch = Watch.constructAutoStartWatch();
            if (aTerminationCriterion.hasToStop()) {
                return SATSolverResult.timeout(watch.getElapsedTime());
            }
            log.info("There are {} variables", aCNF.getVariables().size());
            IntByReference intByReference = null;
            try {
                dccaLibrary.initProblem(aCNF.toDIMACS(null), aSeed);
                lock.lock();
                isCurrentlySolving.set(true);
                lock.unlock();
                long[] previousAssignmentArray = aPreviousAssignment.entrySet().stream().mapToLong(entry -> entry.getKey() * (entry.getValue() ? 1 : -1)).toArray();
                double cutoff = aTerminationCriterion.getRemainingTime();
                log.info("Sending cutoff of {} s", cutoff);
                if (cutoff > 0) {
                    intByReference = dccaLibrary.solveProblem(previousAssignmentArray, previousAssignmentArray.length, cutoff);
                    lock.lock();
                    isCurrentlySolving.set(true);
                    lock.unlock();
                    if (intByReference == null) {
                        log.info("Timed out");
                    } else {
                        final HashSet<Literal> literals = parseAssignment(intByReference);
                        final SATSolverResult output = new SATSolverResult(SATResult.SAT, watch.getElapsedTime(), literals, SolvedBy.DCCA);
                        return output;
                    }
                }
                return new SATSolverResult(SATResult.TIMEOUT, 30.0, ImmutableSet.of(), SolvedBy.UNSOLVED);
            } finally {
                dccaLibrary.destroyProblem(intByReference);
            }
        }

        @Override
        public void interrupt() {
            lock.lock();
            if (isCurrentlySolving.get()) {
                dccaLibrary.interrupt();
            }
            lock.unlock();
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
            int[] assignment = assignmentReference.getPointer().getIntArray(0, size + 1);
            HashSet<Literal> set = new HashSet<>();
            for (int i = 1; i <= size; i++) {
                boolean sign = assignment[i] > 0;
                Literal aLit = new Literal(i, sign);
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
            final SATCompressor aCompressor = new SATCompressor(getConstraintManager());
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
