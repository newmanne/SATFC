package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableMap;

/**
 * Created by newmanne on 2016-05-20.
 */ // Class to store attributes of a station that do not change during the course of the simulation
@RequiredArgsConstructor
public class StationInfo implements IStationInfo {

    @Getter
    private final int id;
    @Getter
    private final Double volume;
    @Getter
    private final Map<Band, Double> values;
    @Getter
    private final Nationality nationality;
    @Getter
    private final Band homeBand;
    @Getter
    private final ImmutableSet<Integer> domain;

    public static StationInfo canadianStation(int id, Band band, Set<Integer> domain) {
        return new StationInfo(id, null, null, Nationality.CA, band, ImmutableSet.copyOf(domain));
    }

    private double getUtility(Band band, double price) {
        double valueForBand = getValue(band);
        return valueForBand - price;
    }

    public Bid queryPreferredBand(Map<Band, Double> offers, Band currentBand) {
        Preconditions.checkState(offers.get(homeBand) == 0, "Station being offered compensation for exiting!");
        final Map<Band, Double> utilityOffers = offers.entrySet().stream().collect(toImmutableMap(Map.Entry::getKey, s -> getUtility(s.getKey(), s.getValue())));
        final Ordering<Band> primaryOrder = Ordering.natural().onResultOf(Functions.forMap(utilityOffers)).reverse();
        final Ordering<Band> secondary = Ordering.natural().onResultOf(Band::ordinal).reverse();
        final Ordering<Band> compound = primaryOrder.compound(secondary);
        final ArrayList<Band> bestOffers = Lists.newArrayList(ImmutableSortedMap.copyOf(offers, compound).descendingKeySet());
        final Band primary = bestOffers.get(0);
        Band fallback = null;
        if (primary.equals(getHomeBand()) || primary.equals(currentBand)) {
            fallback = bestOffers.get(1);
        }
        return new Bid(primary, fallback);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        IStationInfo other = (IStationInfo) obj;
        if (id != other.getId())
            return false;
        return true;
    }

}
