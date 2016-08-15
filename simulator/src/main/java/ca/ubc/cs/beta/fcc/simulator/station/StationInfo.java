package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.ImmutableSet;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-05-20.
 */ // Class to store attributes of a station that do not change during the course of the simulation
// TODO: hash / equals by id onlys
@Data
public class StationInfo implements IStationInfo {

    private final int id;
    private final Double volume;
    private final Double value;
    private final Nationality nationality;
    private final Band homeBand;
    private final ImmutableSet<Integer> domain;

    public static StationInfo canadianStation(int id, Band band, Set<Integer> domain) {
        return new StationInfo(id, null, null, Nationality.CA, band, ImmutableSet.copyOf(domain));
    }

    @Override
    public Band queryPreferredBand(Map<Band, Double> offers) {
        throw  new IllegalStateException();
    }

}
