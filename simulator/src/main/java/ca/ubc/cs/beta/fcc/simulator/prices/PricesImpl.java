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
public class PricesImpl<T> implements IPrices<T> {

    private final Table<IStationInfo, Band, T> prices;

    public PricesImpl() {
        prices = HashBasedTable.create();
    }

    public void setPrice(IStationInfo station, Band band, T price) {
        prices.put(station, band, price);
    }


    public T getPrice(IStationInfo station, Band band) {
        T price = prices.get(station, band);
        Preconditions.checkNotNull(price, "No price for %s on %s", station, band);
        return price;
    }

    @Override
    public Map<Band, T> getOffers(IStationInfo station) {
        return prices.row(station);
    }

}
