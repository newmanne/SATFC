package ca.ubc.cs.beta.stationpacking.execution.problemgenerators;

import ca.ubc.cs.beta.stationpacking.execution.AProblemReader;

/**
* Created by newmanne on 12/05/15.
*/
public class SingleProblemFromCommandLineProblemReader extends AProblemReader {

    private final SATFCFacadeProblem problem;

    public SingleProblemFromCommandLineProblemReader(SATFCFacadeProblem problem) {
        this.problem = problem;
    }

    @Override
    public SATFCFacadeProblem getNextProblem() {
        return index == 1 ? problem : null;
    }

}
