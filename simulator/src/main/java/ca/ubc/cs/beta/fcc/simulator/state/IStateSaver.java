package ca.ubc.cs.beta.fcc.simulator.state;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.time.TimeTracker;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;

import java.util.Map;

/**
 * Created by newmanne on 2016-05-25.
 */
public interface IStateSaver {

    void saveState(StationDB stationDB, LadderAuctionState state, Map<SATResult, Integer> feasibilityResultDistribution, TimeTracker timeTracker);

    AuctionState restoreState(StationDB stationDB);

}
