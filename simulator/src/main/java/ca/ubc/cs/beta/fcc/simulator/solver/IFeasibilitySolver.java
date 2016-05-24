package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by newmanne on 2016-05-20.
 */
public interface IFeasibilitySolver extends AutoCloseable {

    void getFeasibility(Set<StationInfo> stations, Map<Integer, Integer> previousAssignment, SATFCCallback callback);

    default SATFCResult getFeasibilityBlocking(Set<StationInfo> stations, Map<Integer, Integer> previousAssignment) {
        final AtomicReference<SATFCResult> resultReference = new AtomicReference<>();
        getFeasibility(stations, previousAssignment, new SATFCCallback() {
            @Override
            public void onSuccess(SimulatorProblemReader.SATFCProblemSpecification problem, SATFCResult result) {
                resultReference.set(result);
            }

            @Override
            public void onFailure(SimulatorProblemReader.SATFCProblemSpecification problem, RuntimeException exception) {

            }
        });
        // TODO: This isn't exactly what is meant by a blocking call...
        waitForAllSubmitted();
        return resultReference.get();
    }

    default void waitForAllSubmitted() {
    }

}
