package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader.SATFCProblemSpecification;
import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.SATFCFacadeProblem;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-06-15.
 */
public abstract class AFeasibilitySolverDecorator implements IFeasibilitySolver {

    private final IFeasibilitySolver decorated;

    public AFeasibilitySolverDecorator(IFeasibilitySolver decorated) {
        this.decorated = decorated;
    }

    @Override
    public void getFeasibility(SATFCProblemSpecification problem, SATFCCallback callback) {
        decorated.getFeasibility(problem, callback);
    }

    @Override
    public void waitForAllSubmitted() {
        decorated.waitForAllSubmitted();
    }

    @Override
    public void close() throws Exception {
        decorated.close();
    }
}
