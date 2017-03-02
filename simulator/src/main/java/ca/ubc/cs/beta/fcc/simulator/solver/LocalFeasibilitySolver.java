package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.metricwriters.IMetricWriter;
import ca.ubc.cs.beta.stationpacking.execution.metricwriters.MetricWriterFactory;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCFacadeParameters;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacade;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by newmanne on 2016-05-20.
 */
@Slf4j
public class LocalFeasibilitySolver extends AFeasibilitySolver {

    private SATFCFacade facade;
    private final IMetricWriter metricWriter;

    public LocalFeasibilitySolver(SATFCFacadeParameters facadeParameters) {
        // Set up the facade
        facade = SATFCFacadeBuilder.builderFromParameters(facadeParameters)
                .build();
        metricWriter = MetricWriterFactory.createFromParameters(facadeParameters);
    }

    public LocalFeasibilitySolver(SATFCFacade facade) {
        this.facade = facade;
        metricWriter = new MetricWriterFactory.VoidMetricWriter();
    }

    public void getFeasibility(SimulatorProblem simulatorProblem, SATFCCallback callback) {
        final SimulatorProblemReader.SATFCProblemSpecification problem = simulatorProblem.getSATFCProblem();
        log.info("Waiting on SATFC facade...");
        final SATFCResult solve = facade.solve(
                problem.getProblem().getDomains(),
                problem.getProblem().getPreviousAssignment(),
                problem.getCutoff(),
                problem.getSeed(),
                problem.getStationInfoFolder(),
                problem.getName()
        );
        // The problem is not here
//        log.info("Is solve null in Feas? : " + Boolean.toString(solve == null));
//        log.info("this is solve: " + solve.toString());

        log.info("Back from facade...");
        metricWriter.writeMetrics();
        SATFCMetrics.clear();
        callback.onSuccess(simulatorProblem, SimulatorResult.fromSATFCResult(solve));
    }

    public SimulatorResult getFeasibilityBlocking(SimulatorProblem problem) {
        final AtomicReference<SimulatorResult> resultReference = new AtomicReference<>();
        getFeasibility(problem, (p, result) -> {
            log.info("In callback funct, result is null? " + Boolean.toString(result == null));
            resultReference.set(result);
            log.info("In callback funct par 2, resultReference is set? " + Boolean.toString(resultReference.get() == null));});
        // TODO: this shouldn't wait for ALL... it should just do what it says and wait for one...
        waitForAllSubmitted();
        log.info("In get feasibility blocking, resultReference is set? " + Boolean.toString(resultReference.get() == null));
        return resultReference.get();
    }

    // Swap in the new SATFCFacade, returns the old one. It is up to you to close it.
    public SATFCFacade setFacade(SATFCFacade facade) {
        final SATFCFacade old = this.facade;
        this.facade = facade;
        return old;
    }

    @Override
    public void waitForAllSubmitted() {
        // Do nothing - everything blocks!
    }

    @Override
    public void close() throws Exception {
        facade.close();
        metricWriter.onFinished();
    }
}
