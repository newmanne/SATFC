package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonJNISolvers;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml.EncodingCategory;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.walltime.WalltimeTerminationCriterion;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Created by newmanne on 2017-05-16.
 * For solvers that are wrapped
 * LIMITATIONS: Can't work in a parallel portfolio (no interrupt yet)
 */
@Slf4j
public class ACLibSolver implements ISolver {

    private final static int RETRY_COUNT = 3;


    public ACLibSolver(IProblemEncoder encoder, int seedOffset, String wrapperPath, String parameters, String solverName, EncodingCategory encodingCategory) {
        this.encoder = encoder;
        this.seedOffset = seedOffset;
        if (parameters != null) {
            this.parameters = parameters;
        } else {
            this.parameters = "";
        }
        this.solverName = solverName;
        this.wrapperPath = wrapperPath;
        this.encodingCategory = encodingCategory;
    }

    public interface IProblemDecoder {

        Map<Integer, Set<Station>> decode(String solution);

    }

    public interface IProblemEncoder {

        IProblemDecoder encodeToFile(StationPackingInstance instance, File file) throws IOException;

    }

    private final IProblemEncoder encoder;
    private final int seedOffset;
    private final String parameters;
    private final String solverName;
    private final String wrapperPath;
    private final EncodingCategory encodingCategory;

    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed, int retryCount) {
        if (aTerminationCriterion.hasToStop()) {
            return SolverResult.createTimeoutResult(0);
        }
        final int seed = Math.abs(new Random(aSeed + seedOffset).nextInt());
        File solutionFile = null;
        File problemFile = null;
        String processString = null;
        BufferedReader stdInput = null;
        BufferedReader stdError = null;
        final double timeCriterion = aTerminationCriterion.getRemainingTime();
        try {
            problemFile = File.createTempFile("problemFile", encodingCategory.equals(EncodingCategory.SAT) ? ".cnf" : ".lp");
            solutionFile = File.createTempFile("solutionFile", ".txt");

            // Encode to file
            final IProblemDecoder decoder = encoder.encodeToFile(aInstance, problemFile);

            // Call process
            final Runtime rt = Runtime.getRuntime();
            final double cutOff = aTerminationCriterion.getRemainingTime();
            processString = "python2 " + wrapperPath + " " + problemFile.getCanonicalPath() + " 0 " + cutOff + " 0 " + seed + " --output_solution_file " + solutionFile.getCanonicalPath() + " -algorithm " + solverName + " " + parameters;
            log.debug(processString);
            final Process pr = rt.exec(processString, null, new File(wrapperPath).getParentFile());

            stdInput = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            stdError = new BufferedReader(new InputStreamReader(pr.getErrorStream()));

            try {
                pr.waitFor();
                // TODO: interrupt
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // Read results
            final List<String> solverFileLines = FileUtils.readLines(solutionFile);
            final SATResult satResult = SATResult.valueOf(solverFileLines.get(0));
            final double cputime = Double.parseDouble(solverFileLines.get(1));
            // We could use watch here, but that adds time for python etc. and isn't fair to the solver
            final double walltime = Double.parseDouble(solverFileLines.get(2));
            Map<Integer, Set<Station>> assignment = new HashMap<>();
            if (satResult.equals(SATResult.SAT)) {
                String solution = Joiner.on('\n').join(solverFileLines.subList(3, solverFileLines.size()));
                assignment = decoder.decode(solution);
            }
            final SolverResult solverResult = new SolverResult(satResult, walltime, assignment, SolverResult.SolvedBy.COMMAND_LINE_SOLVER, solverName);
            solverResult.setCpuTime(cputime);
            problemFile.delete();
            solutionFile.delete();
            return solverResult;
        } catch (Exception e) {
            log.error("Error parsing results");
            try {
                log.error("STDOUT, followed by STDERR");
                String s;
                if (stdInput != null) {
                    while ((s = stdInput.readLine()) != null) {
                        log.error(s);
                    }
                }
                if (stdError != null) {
                    while ((s = stdError.readLine()) != null) {
                        log.error(s);
                    }
                }
            } catch (IOException e2) {
                throw new RuntimeException(e2);
            }
            if (solutionFile != null) {
                try {
                    log.error("Solution file {} not in expected format:\nResult\ncputime\nwalltime\n(model)", solutionFile.getCanonicalPath());
                    log.error("File:" + System.lineSeparator() + Files.toString(solutionFile, Charset.defaultCharset()));
//                    log.error("Problem file:" + System.lineSeparator() + Files.toString(problemFile, Charset.defaultCharset()));
                } catch (IOException e1) {
                    throw new RuntimeException(e1);
                }
            }
            log.error("Command:" + processString);
            log.error("Exception in ACLib solver: ", e);
            if (retryCount < RETRY_COUNT) {
                return solve(aInstance, new WalltimeTerminationCriterion(timeCriterion), aSeed, retryCount + 1);
            }
            throw new RuntimeException("Trouble using aclib solver", e);
        }
    }


    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        return solve(aInstance, aTerminationCriterion, aSeed, 0);
    }

    @Override
    public void notifyShutdown() {

    }

    @Override
    public void interrupt() {
        throw new RuntimeException("Not implemented yet");
    }

}
