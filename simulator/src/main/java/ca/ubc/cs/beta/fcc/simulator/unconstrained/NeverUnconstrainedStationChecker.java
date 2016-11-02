package ca.ubc.cs.beta.fcc.simulator.unconstrained;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;

/**
 * Created by newmanne on 2016-10-31.
 * Building the table can be slow, mainly for debug use
 */
public class NeverUnconstrainedStationChecker implements ISimulatorUnconstrainedChecker {
    @Override
    public boolean isUnconstrained(IStationInfo stationInfo, ILadder ladder) {
        return false;
    }
}
