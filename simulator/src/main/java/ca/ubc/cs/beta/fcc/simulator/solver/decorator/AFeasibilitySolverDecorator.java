package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.solver.AFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader.SATFCProblemSpecification;
import ca.ubc.cs.beta.stationpacking.execution.problemgenerators.SATFCFacadeProblem;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-06-15.
 */
public class AFeasibilitySolverDecorator extends AFeasibilitySolver {

    protected final IFeasibilitySolver decorated;

    public AFeasibilitySolverDecorator(IFeasibilitySolver decorated) {
        this.decorated = decorated;
    }


    @Override
    public void close() throws Exception {
        decorated.close();
    }

    @Override
    public void getFeasibility(SATFCProblemSpecification problem, SATFCCallback callback) {
        decorated.getFeasibility(problem, callback);
    }

    @Override
    public SATFCResult getFeasibilityBlocking(SATFCProblemSpecification problem) {
        return decorated.getFeasibilityBlocking(problem);
    }

    @Override
    public void waitForAllSubmitted() {
        decorated.waitForAllSubmitted();
    }
}
