package ca.ubc.cs.beta.stationpacking.execution.problemgenerators.problemparsers;

import java.io.IOException;

import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.SATFCFacadeProblem;

/**
 * Created by newmanne on 2016-03-24.
 */
public interface IProblemParser {

    SATFCFacadeProblem problemFromName(String name) throws IOException;

}
