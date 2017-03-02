package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by newmanne on 2016-05-20.
 */
public interface IFeasibilitySolver extends AutoCloseable {

    void getFeasibility(SimulatorProblem problem, SATFCCallback callback);

    default SimulatorResult getFeasibilityBlocking(SimulatorProblem problem) {
        final AtomicReference<SimulatorResult> resultReference = new AtomicReference<>();
        getFeasibility(problem, (p, result) -> {
//            System.out.print("In callback funct, result is null? " + Boolean.toString(result == null));
            resultReference.set(result);});
        // TODO: this shouldn't wait for ALL... it should just do what it says and wait for one...
        waitForAllSubmitted();
        return resultReference.get();
    }

    void waitForAllSubmitted();

}
