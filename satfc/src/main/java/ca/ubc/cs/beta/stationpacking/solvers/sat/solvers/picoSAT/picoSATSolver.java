package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.picoSAT;

import ca.ubc.cs.beta.stationpacking.polling.IPollingService;
import ca.ubc.cs.beta.stationpacking.polling.ProblemIncrementor;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.Literal;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.base.SATSolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.UBCSATLibrary;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.NativeUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.regex.Matcher;  // added by Peter West ->
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files.*;
import java.nio.file.*;
import java.nio.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.Paths.*;
import java.util.*;
import java.io.BufferedWriter; // ->]

/**
 * Created by peterawest on 16-11-15.
 */
@Slf4j
public class picoSATSolver extends AbstractCompressedSATSolver {

    private UBCSATLibrary fLibrary;
    private final String picoSATPath;
    private final String runsolverPath;
//    private final int seedOffset;
//    private final String fParameters;
    private Pointer fState;
    private final Lock lock = new ReentrantLock();
    // boolean represents whether or not a solve is in progress, so that it is safe to do an interrupt
    private final AtomicBoolean isCurrentlySolving = new AtomicBoolean(false);
//    private final ProblemIncrementor problemIncrementor;
    private final String nickname;
    private double cutOff = 30;
    private SATResult satResult;
    private float walltime;

//    public picoSATSolver(String picosatPath, String parameters, IPollingService pollingService) {
//        this.picosatPath = picosatPath;
//
//    }

    public picoSATSolver(String picoSATPath, String runsolverPath,String parameters, String nickname) {
        log.info("Building picoSATSolver");
        this.picoSATPath = picoSATPath;
        this.runsolverPath = runsolverPath;
        this.nickname = nickname;
//        this.seedOffset = seedOffset;
        String mutableParameters = parameters;
//        if (mutableParameters.contains("-seed ")) {
//            throw new IllegalArgumentException("The parameter string cannot contain a seed as it is given upon a call to solve!" + System.lineSeparator() + mutableParameters);
//        }
//        if (!mutableParameters.contains("-alg ")) {
//            throw new IllegalArgumentException("Missing required UBCSAT parameter: -alg." + System.lineSeparator() + mutableParameters);
//        }
//        if (!mutableParameters.contains("-cutoff ")) {
//            mutableParameters = mutableParameters + " -cutoff max";
//        }
//        String testParameters = mutableParameters + " -seed 1";
//        Pointer jnaProblem = fLibrary.initConfig(testParameters);
//        fLibrary.destroyProblem(jnaProblem);

//        fParameters = mutableParameters;
//        problemIncrementor = new ProblemIncrementor(pollingService, this);
        log.info("Done Building picoSATSolver");
    }

    @Override
    public SATSolverResult solve(CNF aCNF, ITerminationCriterion aTerminationCriterion, long aSeed) {
        return solve(aCNF, null, aTerminationCriterion, aSeed);
    }

    @Override
    public SATSolverResult solve(CNF aCNF, Map<Long, Boolean> aPreviousAssignment, ITerminationCriterion aTerminationCriterion, long aSeed) {

        log.info("using PICOSAT");
        try {

            // create temp files
            File tempIn = File.createTempFile("tempFile",".txt");
            tempIn.deleteOnExit();
            File tempOutPico = File.createTempFile("tempFile",".txt");
            tempOutPico.deleteOnExit();
            File tempOutRunsolver = File.createTempFile("tempFile",".txt");
            tempOutRunsolver.deleteOnExit();


            // Write problem to file
            String problem = aCNF.toDIMACS(null);
            Writer writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(tempIn.getCanonicalPath()), "utf-8"));
            writer.write(problem);
            writer.close();


            // Run picosat process
            Runtime rt = Runtime.getRuntime();
//            File picoSATDir = new File("/Users/peterawest/Desktop/2016_2017/cpsc449/code/picosat/picosat-957");
//            Process pr = rt.exec("./picosat " + tempIn.getCanonicalPath()+ " -o " + tempOut.getCanonicalPath(),null,picoSATDir);

            File picoSATDir = new File(picoSATPath);
            String processString = "";
            processString = processString + runsolverPath + "/runsolver";

            cutOff = aTerminationCriterion.getRemainingTime();
            processString = processString + " -C " + Double.toString(cutOff);
            processString = processString + " -o " + tempOutPico.getCanonicalPath();
            processString = processString + "-w " + tempOutRunsolver.getCanonicalPath();
            processString = processString + "./picosat" + tempIn.getCanonicalPath();
            Process pr = rt.exec(processString,null,picoSATDir);

//            InputStream stream = pr.getInputStream();
//            System.out.println("process running");
//
//            try {
//                pr.waitFor(); }
//            catch (InterruptedException e) {
//                e.printStackTrace();
//            }


            // Create list of lines from tempOut file
            List<String> picoFileLines = Files.readAllLines(Paths.get(tempOutPico.getCanonicalPath()), StandardCharsets.UTF_8);

            List<Integer> assignment = new ArrayList<Integer>();
            Pattern satPattern = Pattern.compile("s SATISFIABLE");
            Pattern unsatPattern = Pattern.compile("s UNSATISFIABLE");
            Pattern unknownPattern = Pattern.compile("s UNKNOWN");
            Pattern indeterminatePattern = Pattern.compile("INDETERMINATE");

            for (String x : picoFileLines) {
//                System.out.println(x);

//                if (x.charAt(0) == "s".charAt(0)){
//                    if (x.substring(2,3).equals("S" ) ) {
////                        System.out.println("it is SATISFIABLE");
//                    }
//                }
                Matcher satMatcher = satPattern.matcher(x);
                Matcher unsatMatcher = unsatPattern.matcher(x);
                Matcher unknownMatcher = unknownPattern.matcher(x);
                Matcher indeterminateMatcher = indeterminatePattern.matcher(x);

                if (satMatcher.find()) {
                    satResult = SATResult.SAT;
                } else if (unsatMatcher.find()) {
                    satResult = SATResult.UNSAT;
                } else if (unknownMatcher.find() || indeterminateMatcher.find()) {
                    satResult = SATResult.TIMEOUT;
                }


                if (x.charAt(0) == "v".charAt(0)){
//                    System.out.println("it is v " + x.substring(2,x.length()));

                    Scanner scanner = new Scanner(x.substring(1));
                    List<Integer> list = new ArrayList<Integer>();
                    while (scanner.hasNextInt()) {
                        list.add(scanner.nextInt());
                    }
                    assignment.addAll(list);
                }
            }


            // Create list of lines from tempOut file
            List<String> runsolverFileLines = Files.readAllLines(Paths.get(tempOutRunsolver.getCanonicalPath()), StandardCharsets.UTF_8);

            Pattern walltimePattern = Pattern.compile("walltime:");
            Pattern timeLimit1Pattern = Pattern.compile("runsolver_max_cpu_time_exceeded");
            Pattern timeLimit2Pattern = Pattern.compile("Maximum CPU time exceeded");
            Pattern memLimitPattern = Pattern.compile("runsolver_max_memory_limit_exceeded");

            for (String x : runsolverFileLines) {

//                if (x.charAt(0) == "s".charAt(0)){
//                    if (x.substring(2,3).equals("S" ) ) {
////                        System.out.println("it is SATISFIABLE");
//                    }
//                }
                Matcher walltimeMatcher = walltimePattern.matcher(x);
                Matcher timeLimit1Matcher = timeLimit1Pattern.matcher(x);
                Matcher timeLimit2Matcher = timeLimit1Pattern.matcher(x);
                Matcher memLimitMatcher = memLimitPattern.matcher(x);


                if (walltimeMatcher.find()) {
                    walltime = Float.parseFloat(x.substring(walltimeMatcher.end()));
                } else if (timeLimit1Matcher.find() || timeLimit2Matcher.find()) {
                    satResult = SATResult.TIMEOUT;
                } else if (memLimitMatcher.find()) {
                    satResult = SATResult.TIMEOUT;
                }


                if (x.charAt(0) == "v".charAt(0)){
//                    System.out.println("it is v " + x.substring(2,x.length()));

                    Scanner scanner = new Scanner(x.substring(1));
                    List<Integer> list = new ArrayList<Integer>();
                    while (scanner.hasNextInt()) {
                        list.add(scanner.nextInt());
                    }
                    assignment.addAll(list);
                }
            }




            tempIn.delete();
            tempOutPico.delete();
            tempOutRunsolver.delete();


            Set<Literal> literalAssignment = new HashSet<Literal>();

            for (Integer x : assignment) {
                literalAssignment.add(new Literal(Math.abs(x), (x>0)));
            }
            log.info("walltime: " + walltime);
            log.info("satResult: " + satResult.toString());
            log.info("assignment: " + literalAssignment.toString());
            return new SATSolverResult(satResult, walltime, literalAssignment,SolverResult.SolvedBy.PICOSAT);



        } catch (IOException e){
            log.info("io exception");
            throw new RuntimeException(e);
        }









    }

    private void checkStatus(boolean status, UBCSATLibrary library, Pointer state) {
        Preconditions.checkState(status, library.getErrorMessage(state));
    }

//    private SATSolverResult getSolverResult(UBCSATLibrary fLibrary, Pointer fState, double runtime) {
//        final SATResult satResult;
//        int resultState = fLibrary.getResultState(fState);
//        HashSet<Literal> assignment = null;
//        if (resultState == 1) {
//            satResult = SATResult.SAT;
//            assignment = getAssignment(fLibrary, fState);
//        }
//        else if (resultState == 2) {
//            satResult = SATResult.TIMEOUT;
//        }
//        else if (resultState == 3) {
//            satResult = SATResult.INTERRUPTED;
//        }
//        else {
//            satResult = SATResult.CRASHED;
//
//        }
//        if(assignment == null) {
//            assignment = new HashSet<>();
//        }
//        return new SATSolverResult(satResult, runtime, assignment, SolverResult.SolvedBy.SATENSTEIN, nickname);
//    }

//    private HashSet<Literal> getAssignment(UBCSATLibrary fLibrary, Pointer fState) {
//        HashSet<Literal> assignment = new HashSet<>();
//        IntByReference pRef = fLibrary.getResultAssignment(fState);
//        int numVars = pRef.getValue();
//        int[] tempAssignment = pRef.getPointer().getIntArray(0, numVars + 1);
//        for (int i = 1; i <= numVars; i++) {
//            int intLit = tempAssignment[i];
//            int var = Math.abs(intLit);
//            boolean sign = intLit > 0;
//            Literal aLit = new Literal(var, sign);
//            assignment.add(aLit);
//        }
//
//        return assignment;
//    }

//    private void setPreviousAssignment(Map<Long, Boolean> aPreviousAssignment) {
//        long[] assignment = new long[aPreviousAssignment.size()];
//        int i = 0;
//        for (Long varID : aPreviousAssignment.keySet()) {
//            if (aPreviousAssignment.get(varID)) {
//                assignment[i] = varID;
//            } else {
//                assignment[i] = -varID;
//            }
//            i++;
//        }
//        fLibrary.initAssignment(fState, assignment, assignment.length);
//    }

    @Override
    public void notifyShutdown() {}

    @Override
    public void interrupt() {}
}