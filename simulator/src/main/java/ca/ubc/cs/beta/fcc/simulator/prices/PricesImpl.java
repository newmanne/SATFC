package ca.ubc.cs.beta.fcc.simulator.prices;

import ca.ubc.cs.beta.fcc.simulator.scoring.IScoringRule;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by newmanne on 2016-06-15.
 */
public class PricesImpl implements Prices {

    private final Table<IStationInfo, Band, Double> prices;

    public PricesImpl() {
        prices = HashBasedTable.create();
    }

    public void setPrice(IStationInfo station, Band band, double price) {
        prices.put(station, band, price);
    }

    public double getPrice(IStationInfo station, Band band) {
        return prices.get(station, band);
    }

}
