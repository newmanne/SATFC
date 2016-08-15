package ca.ubc.cs.beta.fcc.simulator.bidprocessing;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.prices.Prices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import com.google.common.collect.ImmutableList;

import java.util.Collection;

/**
 * Created by newmanne on 2016-07-27.
 */
public interface IStationOrderer {

    ImmutableList<IStationInfo> getQueryOrder(Collection<IStationInfo> stations, Prices prices, ILadder ladder);

}
