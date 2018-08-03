package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.ProblemType;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import lombok.Setter;

/**
 * Created by newmanne on 2018-07-24.
 */
public class DistributedSolverForBidProcessing extends AFeasibilitySolverDecorator {

    private final IFeasibilitySolver alternative;
    @Setter
    private boolean useAlternative;

    public DistributedSolverForBidProcessing(IFeasibilitySolver decorated, IFeasibilitySolver alternative) {
        super(decorated);
        this.alternative = alternative;
    }

    @Override
    public void getFeasibility(SimulatorProblem problem, SATFCCallback callback) {
        if (problem.getProblemType().equals(ProblemType.BID_PROCESSING_HOME_BAND_FEASIBLE)) {
            alternative.getFeasibility(problem, callback);
        } else {
            super.getFeasibility(problem, callback);
        }
    }

}
