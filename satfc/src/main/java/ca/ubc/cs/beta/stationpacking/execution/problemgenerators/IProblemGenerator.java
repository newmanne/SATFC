package ca.ubc.cs.beta.stationpacking.execution.problemgenerators;

import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;

/**
* Created by newmanne on 12/05/15.
*/
public interface IProblemGenerator {

    SATFCFacadeProblem getNextProblem();

    void onPostProblem(SATFCFacadeProblem problem, SATFCResult result);

    default void onFinishedAllProblems() {};
}
