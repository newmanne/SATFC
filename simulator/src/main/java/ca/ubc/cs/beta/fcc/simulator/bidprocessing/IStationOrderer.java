package ca.ubc.cs.beta.fcc.simulator.bidprocessing;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;

import java.util.Collection;
import java.util.List;

/**
 * Created by newmanne on 2016-07-27.
 */
public interface IStationOrderer {

    List<IStationInfo> getQueryOrder(Collection<IStationInfo> stations, IPrices prices, ILadder ladder);

}
