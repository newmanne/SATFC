package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacade;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;

/**
 * Created by newmanne on 2016-05-20.
 */
public class LocalFeasibilitySolver extends AFeasibilitySolver {

    private final SATFCFacade facade;

    public LocalFeasibilitySolver(Simulator.ISATFCProblemSpecGenerator problemGenerator) {
        super(problemGenerator);
        // Set up the facade
        facade = new SATFCFacadeBuilder()
                .build();
    }

    @Override
    protected void solve(SimulatorProblemReader.SATFCProblemSpecification problem, SATFCCallback callback) {
        final SATFCResult solve = facade.solve(
                problem.getProblem().getDomains(),
                problem.getProblem().getPreviousAssignment(),
                problem.getCutoff(),
                problem.getSeed(),
                problem.getStationInfoFolder()
        );
        callback.onSuccess(problem, solve);
    }

    @Override
    public void close() throws Exception {
        facade.close();
    }
}
