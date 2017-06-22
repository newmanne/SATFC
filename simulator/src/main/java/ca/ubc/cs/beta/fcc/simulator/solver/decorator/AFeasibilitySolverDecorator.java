package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.solver.AFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import lombok.Setter;

/**
 * Created by newmanne on 2016-06-15.
 */
public class AFeasibilitySolverDecorator extends AFeasibilitySolver {

    protected IFeasibilitySolver decorated;

    public AFeasibilitySolverDecorator(IFeasibilitySolver decorated) {
        this.decorated = decorated;
    }

    @Override
    public void close() throws Exception {
        decorated.close();
    }

    @Override
    public void getFeasibility(SimulatorProblem problem, SATFCCallback callback) {
        decorated.getFeasibility(problem, callback);
    }

    @Override
    public void waitForAllSubmitted() {
        decorated.waitForAllSubmitted();
    }

    public void setDecorated(IFeasibilitySolver decorated) {
        try {
            this.decorated.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.decorated = decorated;
    }
}
