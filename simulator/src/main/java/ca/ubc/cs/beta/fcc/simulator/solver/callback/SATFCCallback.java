package ca.ubc.cs.beta.fcc.simulator.solver.callback;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;

/**
 * Created by newmanne on 2016-05-20.
 */
public interface SATFCCallback {

    void onSuccess(SimulatorProblemReader.SATFCProblemSpecification problem, SATFCResult result);

    default void onFailure(SimulatorProblemReader.SATFCProblemSpecification problem, RuntimeException exception) {
    }

}
