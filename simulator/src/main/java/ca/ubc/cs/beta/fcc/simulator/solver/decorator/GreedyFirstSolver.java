package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.decorator.AFeasibilitySolverDecorator;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;

/**
 * Created by newmanne on 2016-10-04.
 */
public class GreedyFirstSolver extends AFeasibilitySolverDecorator {

    private final IFeasibilitySolver greedySolver;

    public GreedyFirstSolver(IFeasibilitySolver decorated, IFeasibilitySolver greedySolver) {
        super(decorated);
        this.greedySolver = greedySolver;
    }

    @Override
    public void getFeasibility(SimulatorProblemReader.SATFCProblemSpecification problem, SATFCCallback callback) {
        final SATFCResult result = greedySolver.getFeasibilityBlocking(problem);
        if (result.getResult().equals(SATResult.SAT)) {
            callback.onSuccess(problem, result);
        } else {
            super.getFeasibility(problem, callback);
        }
    }


}
