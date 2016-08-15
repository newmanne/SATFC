package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableSet;

/**
 * Created by newmanne on 2016-06-20.
 */
public interface IStationInfo {

    int getId();
    Double getVolume();
    Double getValue();
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

    Band queryPreferredBand(Map<Band, Double> offers);

}
