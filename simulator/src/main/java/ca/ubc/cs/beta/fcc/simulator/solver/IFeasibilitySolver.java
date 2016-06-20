package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.fcc.simulator.time.TimeTracker;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by newmanne on 2016-05-20.
 */
public interface IFeasibilitySolver extends AutoCloseable {

    void getFeasibility(Set<IStationInfo> stations, Map<Integer, Integer> previousAssignment, SATFCCallback callback);

    default SATFCResult getFeasibilityBlocking(@NonNull Set<IStationInfo> stations, @NonNull Map<Integer, Integer> previousAssignment) {
        final AtomicReference<SATFCResult> resultReference = new AtomicReference<>();
        getFeasibility(stations, previousAssignment, (problem, result) -> resultReference.set(result));
        waitForAllSubmitted();
        return resultReference.get();
    }

    default void waitForAllSubmitted() {
    }

}
