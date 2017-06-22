package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonJNISolvers;

import ca.ubc.cs.beta.stationpacking.polling.IPollingService;
import ca.ubc.cs.beta.stationpacking.polling.ProblemIncrementor;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.Literal;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.ISATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.base.SATSolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by newmanne on 2017-05-15.
 */
@Slf4j
public class CommandLineSolver extends AbstractCompressedSATSolver {

    // IMPORTANT
    // LIMITATIONS: Doens't obey seed, can't work in a parallel portfolio (no interrupt yet)

    final static Pattern SAT_PATTERN = Pattern.compile("s SATISFIABLE");
    final static Pattern UNSAT_PATTERN = Pattern.compile("s UNSATISFIABLE");
    final static Pattern UNKNOWN_PATTERN = Pattern.compile("s UNKNOWN");
    final static Pattern INDETERMINATE_PATTERN = Pattern.compile("INDETERMINATE");

    final static Pattern WALLTIME_PATTERN = Pattern.compile("walltime:");
    final static Pattern TIMELIMIT_1_PATTERN = Pattern.compile("runsolver_max_cpu_time_exceeded");
    final static Pattern TIMELIMIT_2_PATTERN = Pattern.compile("Maximum CPU time exceeded");
    final static Pattern MEM_LIMIT_PATTERN = Pattern.compile("runsolver_max_memory_limit_exceeded");

    private final static int RETRY_COUNT = 3;


    protected final String solverPath;
    protected final String parameters;
    protected final String runsolverPath;
    protected final String nickname;
    protected final int seedOffset;

//    protected Process currentProcess;
//    private final Lock lock = new ReentrantLock();
//    // boolean represents whether or not a solve is in progress, so that it is safe to do an interrupt
//    private final AtomicBoolean isCurrentlySolving = new AtomicBoolean(false);

    protected final ProblemIncrementor problemIncrementor;

    public CommandLineSolver(String solverPath, String runsolverPath, String parameters, int seedOffset, IPollingService pollingService, String nickname) {
        this.solverPath = solverPath;
        this.runsolverPath = runsolverPath;
        this.nickname = nickname;
        this.parameters = parameters;
        this.seedOffset = seedOffset;
        problemIncrementor = new ProblemIncrementor(pollingService, this);
    }

    @Override
    public SATSolverResult solve(CNF aCNF, ITerminationCriterion aTerminationCriterion, long aSeed) {
        return solve(aCNF, null, aTerminationCriterion, aSeed);
    }

    @Override
    public SATSolverResult solve(CNF aCNF, Map<Long, Boolean> aPreviousAssignment, ITerminationCriterion aTerminationCriterion, long aSeed) {
        return solve(aCNF, aPreviousAssignment, aTerminationCriterion, aSeed, 0);
    }

    public SATSolverResult solve(CNF aCNF, Map<Long, Boolean> aPreviousAssignment, ITerminationCriterion aTerminationCriterion, long aSeed, int trial) {
        try {
//            Preconditions.checkState(currentProcess == null, "Went to solve a new problem, but there is a problem in progress!");
            if (aTerminationCriterion.hasToStop()) {
                return SATSolverResult.timeout(0);
            }

            // TODO:
            final int seed = Math.abs(new Random(aSeed + seedOffset).nextInt());

            // create temp files
            final File tempIn = File.createTempFile("tempIn", ".cnf");
            tempIn.deleteOnExit();
            final File tempOut = File.createTempFile("tempOut", ".cnf");
            tempOut.deleteOnExit();
            final File tempRunsolver = File.createTempFile("tempRunsolver", ".cnf");
            tempRunsolver.deleteOnExit();

            // Write problem to file
            final String problem = aCNF.toDIMACS(null);
            FileUtils.writeStringToFile(tempIn, problem);

            // Run process
            final Runtime rt = Runtime.getRuntime();
            final double cutOff = aTerminationCriterion.getRemainingTime();
            // TODO: Handle seed!!!! (probably have to subclass for every solver)
            final String processString = runsolverPath + " -C " + Double.toString(cutOff) +
                    " -o " + tempOut.getCanonicalPath() + " -w " + tempRunsolver.getCanonicalPath() + " "
                    + solverPath + " " + tempIn.getCanonicalPath() + parameters;
            final Process pr = rt.exec(processString, null, new File(solverPath).getParentFile());

            try {
                pr.waitFor();
                // TODO: interrupt
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }


            // Create list of lines from tempOut file
            final List<String> solverFileLines = FileUtils.readLines(tempOut);

            final Set<Literal> literalAssignment = new HashSet<>();

            SATResult satResult = SATResult.TIMEOUT;

            for (String x : solverFileLines) {
                if (x.length() > 0) {
                    final Matcher satMatcher = SAT_PATTERN.matcher(x);
                    final Matcher unsatMatcher = UNSAT_PATTERN.matcher(x);
                    final Matcher unknownMatcher = UNKNOWN_PATTERN.matcher(x);
                    final Matcher indeterminateMatcher = INDETERMINATE_PATTERN.matcher(x);

                    if (satMatcher.find()) {
                        satResult = SATResult.SAT;
                    } else if (unsatMatcher.find()) {
                        satResult = SATResult.UNSAT;
                    } else if (unknownMatcher.find() || indeterminateMatcher.find()) {
                        satResult = SATResult.TIMEOUT;
                    }

                    if (x.charAt(0) == "v".charAt(0)) {
                        final Scanner scanner = new Scanner(x.substring(1));
                        while (scanner.hasNextInt()) {
                            int val = scanner.nextInt();
                            literalAssignment.add(new Literal(Math.abs(val), val > 0));
                        }
                    }
                }
            }

            // read runsolver output
            final List<String> runsolverFileLines = FileUtils.readLines(tempRunsolver);

            double walltime = 0;
            boolean wallTimeFound = false;
            for (String x : runsolverFileLines) {
                Matcher walltimeMatcher = WALLTIME_PATTERN.matcher(x);
                Matcher timeLimit1Matcher = TIMELIMIT_1_PATTERN.matcher(x);
                Matcher timeLimit2Matcher = TIMELIMIT_2_PATTERN.matcher(x);

                if (walltimeMatcher.find()) {
                    wallTimeFound = true;
                    walltime = Float.parseFloat(x.substring(walltimeMatcher.end()));
                }
                if (timeLimit1Matcher.find() || timeLimit2Matcher.find()) {
                    // Runsolver inflicted timeout
                    satResult = SATResult.TIMEOUT;
                }
            }

            Preconditions.checkState(wallTimeFound, "Could not read walltime from runsolver");
            // Files will delete on exit, but since we might run for a while, let's clean up
            tempIn.delete();
            tempOut.delete();
            tempRunsolver.delete();

            return new SATSolverResult(satResult, walltime, literalAssignment, getSolvedBy());
        } catch (IOException e) {
            if (trial < RETRY_COUNT) {
                return this.solve(aCNF, aPreviousAssignment, aTerminationCriterion, aSeed, trial + 1);
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void notifyShutdown() {
    }

    protected SolverResult.SolvedBy getSolvedBy() {
        return SolverResult.SolvedBy.COMMAND_LINE_SOLVER;
    }

    @Override
    public void interrupt() {
        throw new RuntimeException("Not implemented");
//        lock.lock();
//        if (isCurrentlySolving.get()) {
//            log.debug("Interrupting {}", nickname);
//            // TODO:
//            log.debug("Back from interrupting {}", nickname);
//        }
//        lock.unlock();
    }

}
