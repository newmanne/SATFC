package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.ladder.LadderEventOnMoveDecorator;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.ProblemType;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.eventbus.Subscribe;
import com.google.common.io.Files;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by newmanne on 2016-11-04.
 */
public class ProblemSaverDecorator extends AFeasibilitySolverDecorator implements AutoCloseable {

    private CSVPrinter problemCSVWriter;
    private CSVPrinter assignmentCSVWriter;

    private final static String INFO_FILE_NAME = "info.csv";
    private final static String PROBLEM_FILE_NAME = "problems.csv";
    private final static String PROBLEM_HEADERS = "ProblemNumber,Stations,Band,Result,CPUTime,WallTime,Greedy,Cached,ProblemType,Round,TargetStation,Name,PreviousAssignmentID";
    private final static String ASSIGNMENT_FILE_NAME = "assignments.csv";
    private final static String ASSIGNMENT_HEADERS = "PreviousAssignmentID,Assignment";
//    private final String ANSWERS_FILE_NAME = "answers.csv";

    private int previousAssignmentNumber;
    private final File problemDir;

    @Builder
    @Data
    public static class ProblemSaverInfo {
        int maxChannel;
        String interference;
    }

    public ProblemSaverDecorator(IFeasibilitySolver decorated, String problemDirName) {
        super(decorated);

        previousAssignmentNumber = -1;

        problemDir = new File(problemDirName);
        Preconditions.checkState(problemDir.exists(), "Problem dir " + problemDir + " was not created in setup!");

        try {
            problemCSVWriter = createCSVWriter(problemDir, PROBLEM_FILE_NAME, PROBLEM_HEADERS);
            assignmentCSVWriter = createCSVWriter(problemDir, ASSIGNMENT_FILE_NAME, ASSIGNMENT_HEADERS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private CSVPrinter createCSVWriter(File dir, String fileName, String headers) throws IOException {
        File problemFile = new File(dir, fileName);
        FileWriter fileWriter = new FileWriter(problemFile, true);
        final BufferedWriter bw = new BufferedWriter(fileWriter);
        final List<String> h = Splitter.on(',').splitToList(headers);
        return new CSVPrinter(bw, CSVFormat.DEFAULT.withHeader(h.toArray(new String[h.size()])));
    }

    @Override
    public void getFeasibility(SimulatorProblem simulatorProblem, SATFCCallback callback) {
        super.getFeasibility(simulatorProblem, (p, r) -> {
            final Object[] record = new Object[] {
                    p.getProblemNumber(),
                    p.getSATFCProblem().getProblem().getDomains().keySet(),
                    p.getBand(),
                    r.getSATFCResult().getResult(),
                    r.getSATFCResult().getCputime(),
                    r.getSATFCResult().getRuntime(),
                    r.isGreedySolved(),
                    r.isCached(),
                    p.getProblemType(),
                    p.getRound(),
                    p.getTargetStation() != null ? p.getTargetStation().getId() : null,
                    p.getSATFCProblem().getName(),
                    previousAssignmentNumber
            };
            try {
                problemCSVWriter.printRecord(record);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            callback.onSuccess(p, r);
        });
    }

    @Subscribe
    public void onMove(LadderEventOnMoveDecorator.LadderMoveEvent moveEvent) {
        final Map<Integer, Integer> previousAssignment = moveEvent.getLadder().getPreviousAssignment();
        writePreviousAssignment(previousAssignment);
    }

    private void writePreviousAssignment(Map<Integer, Integer> previousAssignment) {
        final Object[] record = new Object[] {
                ++previousAssignmentNumber,
                JSONUtils.toString(previousAssignment)
        };
        try {
            assignmentCSVWriter.printRecord(record);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeInfo(ProblemSaverInfo info) {
        try {
            Files.write(JSONUtils.toString(info), new File(problemDir, INFO_FILE_NAME), Charsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeStartingAssignment(Map<Integer, Integer> previousAssignment) {
        Preconditions.checkState(previousAssignmentNumber == -1, "Previous assignment number is not 0!");
        writePreviousAssignment(previousAssignment);
    }

    public void close() throws Exception {
        super.close();
        if (problemCSVWriter != null) {
            problemCSVWriter.close();
        } else if (assignmentCSVWriter != null) {
            assignmentCSVWriter.close();
        }
    }

}
