package ca.ubc.cs.beta.fcc.simulator.solver.callback;

import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;

/**
 * Created by newmanne on 2016-05-20.
 */
public interface SATFCCallback {

    void onSuccess(SimulatorProblem problem, SimulatorResult result);

    default void onFailure(SimulatorProblem problem, RuntimeException exception) {
    }

}
