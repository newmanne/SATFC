package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableMap;

/**
 * Created by newmanne on 2016-05-20.
 */ // Class to store attributes of a station that do not change during the course of the simulation
@Slf4j
public class StationInfo implements IStationInfo {

    @Getter
    private final int id;
    @Getter
    private final Nationality nationality;
    @Getter
    private final Band homeBand;
    @Getter
    private ImmutableSet<Integer> domain;
    // What you read out of Domain.csv, never modified
    @Getter
    private final ImmutableSet<Integer> fullDomain;
    @Getter
    private final String city;
    @Getter
    private final String call;
    @Getter
    private final int population;

    @Getter
    @Setter
    private Integer volume;
    @Getter
    @Setter
    private Map<Band, Double> values;

    private int maxChan = StationPackingUtils.UHFmax;
    private int minChan = StationPackingUtils.LVHFmin;

    public static StationInfo canadianStation(int id, Band band, Set<Integer> domain, String city, String call, int pop) {
        return new StationInfo(id, Nationality.CA, band, ImmutableSet.copyOf(domain), city, call, pop);
    }

    public StationInfo(int id, Nationality nationality, Band band, Set<Integer> domain, String city, String call, int population) {
        this.id = id;
        this.nationality = nationality;
        this.homeBand = band;
        this.fullDomain = ImmutableSet.copyOf(domain);
        this.domain = this.fullDomain;
        this.city = city;
        this.call = call;
        this.population = population;
    }

    private void adjustDomain() {
        this.domain = fullDomain.stream().filter(c -> c <= maxChan && c >= minChan).collect(GuavaCollectors.toImmutableSet());
        if (getHomeBand().equals(Band.UHF)) {
            Preconditions.checkState(!getDomain(Band.UHF).isEmpty(), "UHF band domain emptied!");
        }
    }

    public void setMaxChannel(int c) {
        this.maxChan = c;
        adjustDomain();
    }

    public void setMinChannel(int c) {
        this.minChan = c;
        adjustDomain();
    }

    private double getUtility(Band band, double payment) {
        return getValue(band) + payment;
    }

    public Bid queryPreferredBand(Map<Band, Double> offers, Band currentBand) {
        Preconditions.checkState(offers.get(homeBand) == 0, "Station being offered compensation for exiting!");
        final Map<Band, Double> utilityOffers = offers.entrySet().stream().collect(toImmutableMap(Map.Entry::getKey, s -> getUtility(s.getKey(), s.getValue())));
        final Ordering<Band> primaryOrder = Ordering.natural().onResultOf(Functions.forMap(utilityOffers));
        final Ordering<Band> compound = primaryOrder.compound(Comparator.comparingInt(Band::ordinal));
        final ArrayList<Band> bestOffers = Lists.newArrayList(ImmutableSortedMap.copyOf(offers, compound).descendingKeySet());
        final Band primary = bestOffers.get(0);
        Band fallback = null;
        if (!Bid.isSafe(primary, currentBand, getHomeBand())) {
            int i = 1;
            fallback = bestOffers.get(i);
            while (!Bid.isSafe(fallback, currentBand, getHomeBand())) {
                i++;
                fallback = bestOffers.get(i);
            }
        }
        Preconditions.checkState((fallback == null && Bid.isSafe(primary, currentBand, getHomeBand())) || Bid.isSafe(fallback, currentBand, getHomeBand()));
        return new Bid(primary, fallback);
    }

    @Override
    public String toString() {
        return call + " (" + Integer.toString(id) + ", HB=" + homeBand + ")";
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
