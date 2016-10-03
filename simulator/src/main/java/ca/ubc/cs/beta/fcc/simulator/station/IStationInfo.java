package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableSet;

/**
 * Created by newmanne on 2016-06-20.
 */
public interface IStationInfo {

    int getId();
    Double getVolume();
    Map<Band, Double> getValues();
    default double getValue(Band band) {
        Preconditions.checkState(getValues().containsKey(band), "Station %s has no value for band %s", getId(), band);
        return getValues().get(band);
    }
    Nationality getNationality();
    Band getHomeBand();
    ImmutableSet<Integer> getDomain();
    default ImmutableSet<Integer> getDomain(Band band) {
        final Set<Integer> domain = getDomain();
        final Set<Integer> bandChannels = BandHelper.toChannels(band);
        return ImmutableSet.copyOf(Sets.intersection(domain, bandChannels));
    }
    default ImmutableSet<Integer> getDomain(Collection<Band> bands) {
        return bands.stream().flatMap(band -> getDomain(band).stream()).collect(toImmutableSet());
    }

    Bid queryPreferredBand(Map<Band, Double> offers, Band currentBand);

}
