package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import ca.ubc.cs.beta.stationpacking.base.Station;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-06-20.
 */
public interface IStationInfo {

    int getId();

    Integer getVolume();

    Map<Band, Long> getValues();

    default long getValue(Band band) {
        Preconditions.checkState(getValues().containsKey(band), "Station %s has no value for band %s", getId(), band);
        return getValues().get(band);
    }

    ImmutableSet<String> nonMainlandDMAs = ImmutableSet.of("Honolulu, HI", "Virgin Islands", "Anchorage, AK", "Fairbanks, AK", "Juneau, AK", "Puerto Rico");

    default boolean isMainland() {
        return !nonMainlandDMAs.contains(getDMA());
    }

    default long getValue() {
        return getValue(getHomeBand());
    }

    Nationality getNationality();

    Band getHomeBand();

    ImmutableSet<Integer> getDomain();

    ImmutableSet<Integer> getFullDomain();

    default ImmutableSet<Integer> getDomain(Band band) {
        final Set<Integer> domain = getDomain();
        final Set<Integer> bandChannels = BandHelper.toChannels(band);
        return ImmutableSet.copyOf(Sets.intersection(domain, bandChannels));
    }

    default ImmutableSet<Integer> getFullDomain(Band band) {
        final Set<Integer> domain = getFullDomain();
        final Set<Integer> bandChannels = BandHelper.toChannels(band);
        return ImmutableSet.copyOf(Sets.intersection(domain, bandChannels));
    }

    default Boolean isParticipating(Map prices) {
        return null;
    }

    String getCity();

    String getCall();

    int getPopulation();

    Bid queryPreferredBand(Map<Band, Long> offers, Band currentBand);

    Boolean isCommercial();

    boolean isEligible();

    String getDMA();

    default Station toSATFCStation() {
        return new Station(getId());
    }

    void impair();

    void unimpair();

    boolean isImpaired();
}
