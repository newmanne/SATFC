package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.metricwriters.IMetricWriter;
import ca.ubc.cs.beta.stationpacking.execution.metricwriters.MetricWriterFactory;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCFacadeParameters;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacade;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;

/**
 * Created by newmanne on 2016-05-20.
 */
public class LocalFeasibilitySolver extends AFeasibilitySolver {

    private final SATFCFacade facade;
    private final IMetricWriter metricWriter;

    public LocalFeasibilitySolver(SATFCFacadeParameters facadeParameters) {
        // Set up the facade
        facade = SATFCFacadeBuilder.builderFromParameters(facadeParameters)
                .build();
        metricWriter = MetricWriterFactory.createFromParameters(facadeParameters);
    }

    @Override
    protected void solve(SimulatorProblemReader.SATFCProblemSpecification problem, SATFCCallback callback) {
        final SATFCResult solve = facade.solve(
                problem.getProblem().getDomains(),
                problem.getProblem().getPreviousAssignment(),
                problem.getCutoff(),
                problem.getSeed(),
                problem.getStationInfoFolder(),
                problem.getName()
        );
        metricWriter.writeMetrics();
        SATFCMetrics.clear();
        callback.onSuccess(problem, solve);
    }

    @Override
    public void close() throws Exception {
        facade.close();
        metricWriter.onFinished();
    }
}
