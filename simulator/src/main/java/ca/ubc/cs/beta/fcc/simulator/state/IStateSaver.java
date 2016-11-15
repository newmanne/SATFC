package ca.ubc.cs.beta.fcc.simulator.state;

import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;

/**
 * Created by newmanne on 2016-05-25.
 */
public interface IStateSaver {

    void saveState(IStationDB stationDB, LadderAuctionState state);

    AuctionState restoreState(IStationDB stationDB);

}
