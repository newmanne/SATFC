package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.solver.AFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.base.SATSolverResult;
import com.google.common.collect.ImmutableMap;

/**
 * Created by newmanne on 2016-12-10.
 */
public class VoidFeasibilitySolver extends AFeasibilitySolver {

    @Override
    public void getFeasibility(SimulatorProblem problem, SATFCCallback callback) {
        callback.onSuccess(problem, SimulatorResult.builder().SATFCResult(new SATFCResult(SATResult.TIMEOUT, 0, 0, ImmutableMap.of())).build());
    }

    @Override
    public void waitForAllSubmitted() {
    }

    @Override
    public void close() throws Exception {
    }
}
