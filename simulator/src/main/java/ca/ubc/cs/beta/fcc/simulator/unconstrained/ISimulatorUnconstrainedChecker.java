package ca.ubc.cs.beta.fcc.simulator.unconstrained;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;

/**
 * Created by newmanne on 2016-09-27.
 */
public interface ISimulatorUnconstrainedChecker {

    boolean isUnconstrained(IStationInfo stationInfo, ILadder ladder);

}
