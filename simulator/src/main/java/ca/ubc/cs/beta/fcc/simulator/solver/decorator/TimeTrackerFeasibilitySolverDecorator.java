package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.fcc.simulator.time.TimeTracker;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-06-15.
 */
public class TimeTrackerFeasibilitySolverDecorator extends AFeasibilitySolverDecorator {

    private final TimeTracker timeTracker;

    public TimeTrackerFeasibilitySolverDecorator(IFeasibilitySolver decorated, TimeTracker timeTracker) {
        super(decorated);
        this.timeTracker = timeTracker;
    }

    @Override
    public void getFeasibility(Set<StationInfo> stations, Map<Integer, Integer> previousAssignment, SATFCCallback callback) {
        super.getFeasibility(stations, previousAssignment, (problem, result) -> {
            timeTracker.update(result);
            callback.onSuccess(problem, result);
        });
    }

}
