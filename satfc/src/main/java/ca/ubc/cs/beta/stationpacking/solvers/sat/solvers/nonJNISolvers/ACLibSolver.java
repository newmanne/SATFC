package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonJNISolvers;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml.EncodingCategory;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by newmanne on 2017-05-16.
 * For solvers that are wrapped
 * LIMITATIONS: Can't work in a parallel portfolio (no interrupt yet)
 */
@Slf4j
public class ACLibSolver implements ISolver {

    public ACLibSolver(IProblemEncoder encoder, int seedOffset, String wrapperPath, String parameters, String solverName, EncodingCategory encodingCategory) {
        this.encoder = encoder;
        this.seedOffset = seedOffset;
        this.parameters = parameters;
        this.solverName = solverName;
        this.wrapperPath = wrapperPath;
        this.encodingCategory = encodingCategory;
    }

    public interface IProblemDecoder {

        Map<Integer,Set<Station>> decode(String solution);

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

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        if (aTerminationCriterion.hasToStop()) {
            return SolverResult.createTimeoutResult(0);
        }
        final int seed = Math.abs(new Random(aSeed + seedOffset).nextInt());

        File problemFile = null;
        File solutionFile = null;
        try {
            problemFile = File.createTempFile("problemFile", encodingCategory.equals(EncodingCategory.SAT) ? ".cnf" : ".lp");
            solutionFile = File.createTempFile("solutionFile", ".txt");
            problemFile.deleteOnExit();
            solutionFile.deleteOnExit();

            // Encode to file
            final IProblemDecoder decoder = encoder.encodeToFile(aInstance, problemFile);

            // Call process
            final Runtime rt = Runtime.getRuntime();
            final double cutOff = aTerminationCriterion.getRemainingTime();
            final String processString = "python " + wrapperPath + " " + problemFile.getCanonicalPath() + " 0 " + cutOff + " 0 " + seed + " --output_solution_file " + solutionFile.getCanonicalPath() + " -algorithm " + solverName + " " + parameters;
            log.info(processString);
            final Process pr = rt.exec(processString, null, new File(wrapperPath).getParentFile());
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
            return solverResult;
        } catch (IOException e) {
            throw new RuntimeException("Trouble using aclib solver", e);
        } finally {
            if (problemFile != null) {
                problemFile.delete();
            }
            if (solutionFile != null) {
                solutionFile.delete();
            }
        }
    }

    @Override
    public void notifyShutdown() {

    }

    @Override
    public void interrupt() {
        throw new RuntimeException("Not implemented yet");
    }

}
