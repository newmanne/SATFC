package ca.ubc.cs.beta.fcc.simulator.solver.callback;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;

/**
 * Created by newmanne on 2016-05-20.
 */
public interface SATFCCallback {

    void onSuccess(Simulator.SATFCProblemSpecification problem, SATFCResult result);

    default void onFailure(Simulator.SATFCProblemSpecification problem, RuntimeException exception) {
    }

}
