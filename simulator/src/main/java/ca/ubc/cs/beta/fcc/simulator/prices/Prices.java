package ca.ubc.cs.beta.fcc.simulator.prices;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;

/**
 * Created by newmanne on 2016-06-15.
 */
public interface Prices {

    void setPrice(IStationInfo station, Double price);

    double getPrice(IStationInfo station);

}
