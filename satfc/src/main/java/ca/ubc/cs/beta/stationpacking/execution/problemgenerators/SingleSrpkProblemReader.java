package ca.ubc.cs.beta.stationpacking.execution.problemgenerators;

import java.io.IOException;

import ca.ubc.cs.beta.stationpacking.execution.AProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.problemparsers.IProblemParser;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by newmanne on 11/06/15.
 */
@Slf4j
public class SingleSrpkProblemReader extends AProblemReader {

    private final String srpkFile;
    private final IProblemParser nameToProblem;

    public SingleSrpkProblemReader(String srpkFile, IProblemParser nameToProblem) {
        this.srpkFile = srpkFile;
        this.nameToProblem = nameToProblem;
    }

    @Override
    public SATFCFacadeProblem getNextProblem() {
        if (index != 1) {
            return null;
        }
        final SATFCFacadeProblem problem;
        try {
            problem = nameToProblem.problemFromName(srpkFile);
        } catch (IOException e) {
            throw new RuntimeException("Error parsing file " + srpkFile, e);
        }
        return problem;
    }

}
