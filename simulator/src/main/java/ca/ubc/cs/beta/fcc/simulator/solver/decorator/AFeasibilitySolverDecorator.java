package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-06-15.
 */
public abstract class AFeasibilitySolverDecorator implements IFeasibilitySolver {

    private final IFeasibilitySolver decorated;

    public AFeasibilitySolverDecorator(IFeasibilitySolver decorated) {
        this.decorated = decorated;
    }

    @Override
    public void getFeasibility(Set<IStationInfo> stations, Map<Integer, Integer> previousAssignment, SATFCCallback callback) {
        decorated.getFeasibility(stations, previousAssignment, callback);
    }

    @Override
    public void waitForAllSubmitted() {
        decorated.waitForAllSubmitted();
    }

    @Override
    public void close() throws Exception {
        decorated.close();
    }
}
