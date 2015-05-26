package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental;

import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.aeatk.concurrent.threadfactory.SequentiallyNamedThreadFactory;
import ca.ubc.cs.beta.aeatk.misc.returnvalues.AEATKReturnValues;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.Literal;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.base.SATSolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.Clasp3Library;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.NativeUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

@Slf4j
public class Clasp3SATSolver extends AbstractCompressedSATSolver {

    private Clasp3Library fClaspLibrary;
    private String fParameters;
    private final ScheduledExecutorService fTimerService = Executors.newScheduledThreadPool(1, new SequentiallyNamedThreadFactory("Clasp SAT Solver Timers", true));

    /**
     * Integer flag that we use to keep track of our current request, cutoff and timer threads will only execute if
     * this matches the id when they started.
     */
    private final AtomicLong currentRequestID = new AtomicLong(1);
    private final Lock lock = new ReentrantLock();
    private Pointer currentProblemPointer;
    // boolean represents whether or not a solve is in progress, so that it is safe to do an interrupt
    private final AtomicBoolean isCurrentlySolving = new AtomicBoolean(false);

    public Clasp3SATSolver(String libraryPath, String parameters) {
        this((Clasp3Library) Native.loadLibrary(libraryPath, Clasp3Library.class, NativeUtils.NATIVE_OPTIONS), parameters);
    }

    public Clasp3SATSolver(Clasp3Library library, String parameters) {
        fClaspLibrary = library;
        fParameters = parameters;
        // set the info about parameters, throw an exception if seed is contained.
        if (parameters.contains("--seed")) {
            throw new IllegalArgumentException("The parameter string cannot contain a seed as it is given upon a call to solve!");
        }
        // make sure the configuration is valid
        String params = fParameters + " --seed=1";
        Pointer jnaProblem = fClaspLibrary.initConfig(params);
        try {
            int status = fClaspLibrary.getConfigState(jnaProblem);
            if (status == 2) {
                throw new IllegalArgumentException(fClaspLibrary.getConfigErrorMessage(jnaProblem));
            }
        } finally {
            fClaspLibrary.destroyProblem(jnaProblem);
        }
    }

    /*
     * (non-Javadoc)
     * NOT THREAD SAFE!
     * @see ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.ISATSolver#solve(ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF, double, long)
     */
    @Override
    public SATSolverResult solve(CNF aCNF, ITerminationCriterion aTerminationCriterion, long aSeed) {
        Watch watch = Watch.constructAutoStartWatch();
        final long MY_REQUEST_ID = currentRequestID.incrementAndGet();
        final int seed = Math.abs(new Random(aSeed).nextInt());
        final String params = fParameters + " --seed=" + seed;
        try {
            // create the problem - config params have already been validated in the constructor, so this should work
            if (currentProblemPointer != null) {
                throw new IllegalStateException("Went to solve a new problem, but there is a problem in progress!");
            }
            if (aTerminationCriterion.hasToStop()) {return SATSolverResult.timeout(watch.getElapsedTime());}

            currentProblemPointer = fClaspLibrary.initConfig(params);

            if (aTerminationCriterion.hasToStop()) {return SATSolverResult.timeout(watch.getElapsedTime());}

            fClaspLibrary.initProblem(currentProblemPointer, aCNF.toDIMACS(null));

            if (aTerminationCriterion.hasToStop()) {return SATSolverResult.timeout(watch.getElapsedTime());}

            // We lock this variable so that the interrupt code will only execute if there is a valid problem to interrupt
            lock.lock();
            isCurrentlySolving.set(true);
            lock.unlock();

            double preTime = watch.getElapsedTime();
            final double cutoff = aTerminationCriterion.getRemainingTime();
            if (cutoff <= 0 || aTerminationCriterion.hasToStop()) {
                return SATSolverResult.timeout(watch.getElapsedTime());
            }

            //launches a suicide SATFC time that just kills everything if it finishes and we're still on the same job.
            final int SUICIDE_GRACE_IN_SECONDS = 5 * 60;
            Future<?> suicideFuture = fTimerService.schedule(
                    () -> {
                        if (MY_REQUEST_ID == currentRequestID.get()) {
                            log.error("Clasp has spent {} more seconds than expected ({}) on current run, killing everything (i.e. System.exit(1) ).", SUICIDE_GRACE_IN_SECONDS, cutoff);
                            System.exit(AEATKReturnValues.OH_THE_HUMANITY_EXCEPTION);
                        }
                    }, (long) cutoff + SUICIDE_GRACE_IN_SECONDS, TimeUnit.SECONDS);

            // Start solving
            log.debug("Send problem to clasp cutting off after " + cutoff + "s");

            fClaspLibrary.solveProblem(currentProblemPointer, cutoff);
            final double runtime = watch.getElapsedTime() - preTime;
            log.debug("Came back from clasp after {}s.", runtime);
            lock.lock();
            isCurrentlySolving.set(false);
            lock.unlock();

            if (aTerminationCriterion.hasToStop()) {return SATSolverResult.timeout(watch.getElapsedTime());}

            final ClaspResult claspResult = getSolverResult(fClaspLibrary, currentProblemPointer, runtime);
            final double timeToParseClaspResult = watch.getElapsedTime() - runtime - preTime;
            log.trace("Time to parse clasp result: {} s.", timeToParseClaspResult);

            if (aTerminationCriterion.hasToStop()) {return SATSolverResult.timeout(watch.getElapsedTime());}

            final HashSet<Literal> assignment = parseAssignment(claspResult.getAssignment());

            if (aTerminationCriterion.hasToStop()) {return SATSolverResult.timeout(watch.getElapsedTime());}

            log.trace("Time to parse assignment: {} s.", watch.getElapsedTime() - runtime - preTime - timeToParseClaspResult);
            final double postTime = watch.getElapsedTime() - runtime - preTime;
            log.trace("Total post time: {} s.", postTime);
            if (postTime > 60) {
                log.error("Clasp SAT solver post solving time was greater than 1 minute, something wrong must have happened.");
            }

            log.debug("Incrementing job number.");
            currentRequestID.incrementAndGet();

            log.debug("Cancelling suicide future.");
            suicideFuture.cancel(true);

            final SATSolverResult output = new SATSolverResult(claspResult.getSATResult(), claspResult.getRuntime() + preTime + postTime, assignment);
            log.debug("Returning result: {}.", output);
            return output;
        } finally {
            if (currentProblemPointer != null) {
                log.trace("Destroying problem");
                lock.lock();
                isCurrentlySolving.set(false);
                lock.unlock();
                fClaspLibrary.destroyProblem(currentProblemPointer);
                currentProblemPointer = null;
            }
        }
    }

    @Override
    public void notifyShutdown() {
        fTimerService.shutdown();
    }

    @Override
    public void interrupt() {
        lock.lock();
        if (isCurrentlySolving.get()) {
            log.debug("Interrupting clasp");
            fClaspLibrary.interrupt(currentProblemPointer);
            log.debug("Back from interrupting clasp");
        }
        lock.unlock();
    }

    private HashSet<Literal> parseAssignment(int[] assignment) {
        HashSet<Literal> set = new HashSet<>();
        for (int i = 1; i < assignment[0]; i++) {
            int intLit = assignment[i];
            int var = Math.abs(intLit);
            boolean sign = intLit > 0;
            Literal aLit = new Literal(var, sign);
            set.add(aLit);
        }
        return set;
    }

    /**
     * Extract solver result from JNA Clasp library.
     */
    public static ClaspResult getSolverResult(Clasp3Library library,
                                              Pointer problem,
                                              double runtime) {

        final SATResult satResult;
        int[] assignment = {0};
        int state = library.getResultState(problem);
        if (state == 0) {
            satResult = SATResult.UNSAT;
        } else if (state == 1) {
            satResult = SATResult.SAT;
            IntByReference pRef = library.getResultAssignment(problem);
            int size = pRef.getValue();
            assignment = pRef.getPointer().getIntArray(0, size);
        } else if (state == 2) {
            satResult = SATResult.TIMEOUT;
        } else if (state == 3) {
            satResult = SATResult.INTERRUPTED;
        } else {
            satResult = SATResult.CRASHED;
            log.error("Clasp crashed!");
        }

        return new ClaspResult(satResult, assignment, runtime);
    }

}