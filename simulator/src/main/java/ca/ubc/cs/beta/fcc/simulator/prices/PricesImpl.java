package ca.ubc.cs.beta.fcc.simulator.prices;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Created by newmanne on 2016-06-15.
 */
public class PricesImpl implements IPrices {

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
