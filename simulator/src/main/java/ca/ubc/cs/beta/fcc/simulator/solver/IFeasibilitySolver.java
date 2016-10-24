package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader.SATFCProblemSpecification;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by newmanne on 2016-05-20.
 */
public interface IFeasibilitySolver extends AutoCloseable {

    void getFeasibility(SATFCProblemSpecification problem, SATFCCallback callback);

    default SATFCResult getFeasibilityBlocking(SATFCProblemSpecification problem) {
        final AtomicReference<SATFCResult> resultReference = new AtomicReference<>();
        getFeasibility(problem, (p, result) -> resultReference.set(result));
        // TODO: this shouldn't wait for ALL... it should just do what it says and wait for one...
        waitForAllSubmitted();
        return resultReference.get();
    }

    void waitForAllSubmitted();

}
