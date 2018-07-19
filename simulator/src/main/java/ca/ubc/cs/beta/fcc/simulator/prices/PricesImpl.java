package ca.ubc.cs.beta.fcc.simulator.prices;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Created by newmanne on 2016-06-15.
 */
@Slf4j
public class PricesImpl implements IPrices {

    private final Table<IStationInfo, Band, Double> prices;

    public PricesImpl() {
        prices = HashBasedTable.create();
    }

    public void setPrice(IStationInfo station, Band band, double price) {
        prices.put(station, band, price);
    }

    public double getPrice(IStationInfo station, Band band) {
        Double price = prices.get(station, band);
        if (price == null) {
            log.info(prices.toString());
        }
        Preconditions.checkNotNull(price, "No price for %s on %s", station, band);
        return price;
    }

    @Override
    public Map<Band, Double> getOffers(IStationInfo station) {
        return prices.row(station);
    }

}
