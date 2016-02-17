package ca.ubc.cs.beta.stationpacking.execution.problemgenerators;

import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.SATFCFacadeProblem;

/**
 * Created by newmanne on 2016-02-16.
 */
public interface ICutoffChooser {

    double getCutoff(SATFCFacadeProblem problem);

}
