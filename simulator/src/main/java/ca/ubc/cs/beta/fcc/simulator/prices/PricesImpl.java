package ca.ubc.cs.beta.fcc.simulator.prices;

import ca.ubc.cs.beta.fcc.simulator.scoring.IScoringRule;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by newmanne on 2016-06-15.
 */
public class PricesImpl implements Prices {

    public PricesImpl() {
        prices = new ConcurrentHashMap<>();
    }

    public PricesImpl(StationDB stationDB, IScoringRule scoringRule) {
        this();
        for (final StationInfo s : stationDB.getStations()) {
            if (s.getNationality().equals(Nationality.CA)) {
                continue;
            }
            setPrice(s, scoringRule.score(s));
        }

    }

    private final Map<StationInfo, Double> prices;

    public void setPrice(IStationInfo station, Double price) {
        prices.put(station, price);
    }

    public double getPrice(IStationInfo stationID) {
        return prices.get(stationID);
    }

}
