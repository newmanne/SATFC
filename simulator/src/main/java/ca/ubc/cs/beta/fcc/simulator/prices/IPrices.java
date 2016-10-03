package ca.ubc.cs.beta.fcc.simulator.prices;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.ImmutableMap;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableMap;

/**
 * Created by newmanne on 2016-06-15.
 */
public interface IPrices {

    void setPrice(IStationInfo station, Band band, double price);

    double getPrice(IStationInfo station, Band band);

    default ImmutableMap<Band, Double> getPrices(IStationInfo station, Collection<Band> bands) {
        return bands.stream().collect(toImmutableMap(band -> band, band -> getPrice(station, band)));
    }

}
