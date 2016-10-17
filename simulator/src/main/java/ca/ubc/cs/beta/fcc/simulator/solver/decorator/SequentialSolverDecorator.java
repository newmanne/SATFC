package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;

/**
 * The idea here is that you want to try solvers in sequence
 * E.g. it takes 1 core to run a Stein presolver, and we know this solves most problems. Whereas a "full" SATFC is 8 cores - this is a waste of capacity.
 */
public class SequentialSolverDecorator extends AFeasibilitySolverDecorator {

    private final IFeasibilitySolver firstSolver;

    public SequentialSolverDecorator(IFeasibilitySolver decorated, IFeasibilitySolver firstSolver) {
        super(decorated);
        this.firstSolver = firstSolver;
    }

    @Override
    public void getFeasibility(SimulatorProblemReader.SATFCProblemSpecification problem, SATFCCallback callback) {
        final SATFCResult result = firstSolver.getFeasibilityBlocking(problem);
        if (result.getResult().isConclusive()) {
            callback.onSuccess(problem, result);
        } else {
            super.getFeasibility(problem, callback);
        }
    }


}
