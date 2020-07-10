package ca.ubc.cs.beta.fcc.simulator.utils;

/**
 * Created by newmanne on 2016-07-29.
 */

import ca.ubc.cs.beta.fcc.simulator.clearingtargetoptimization.ClearingTargetOptimizationMIP;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.util.Set;
import java.util.stream.IntStream;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableSet;

/**
 * Band related helper functions.
 *
 * @author afrechet
 */
public class BandHelper {

    private BandHelper() {
        // Cannot construct instance of a helper class.
    }

    /**
     * Global static constant containing the VHF bands.
     */
    public static final ImmutableSet<Band> VHF_BANDS = ImmutableSet.of(Band.LVHF, Band.HVHF);

    /**
     * Global static constant containing the non OFF-air bands.
     */
    public static final ImmutableSet<Band> AIR_BANDS = ImmutableSet.of(Band.LVHF, Band.HVHF, Band.UHF);

    // TODO: this is very hacky, and means you can't do two clearing targets in the same JVM...
    private static ImmutableSet<Integer> UHF_CHANNELS = null;

    public static final int OFF_BAND_CHANNEL = 0;

    /**
     * @param aBand - a band.
     * @return the set of integer channels corresponding to the given band.
     */
    public static Set<Integer> toChannels(Band aBand) {
        switch (aBand) {
            case UHF:
                Preconditions.checkNotNull(UHF_CHANNELS, "UHF channels haven't been set yet.");
                return UHF_CHANNELS;
            case HVHF:
                return StationPackingUtils.HVHF_CHANNELS;
            case LVHF:
                return StationPackingUtils.LVHF_CHANNELS;
            case OFF:
                throw new IllegalArgumentException("OFF band does not have channels.");
            default:
                throw new IllegalStateException("Unrecognized band " + aBand + ".");
        }
    }


    /**
     * @param aChannel - an integer channel.
     * @return the band corresponding to this channel.
     */
    public static Band toBand(Integer aChannel) {
        Preconditions.checkNotNull(aChannel, "Cannot provide a null channel.");
        if (aChannel == 0) {
            return Band.OFF;
        } else if (aChannel >= 14) {
            Preconditions.checkState(aChannel == ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL || StationPackingUtils.UHF_CHANNELS.contains(aChannel), "Channel %s not in UHF", aChannel);
            return Band.UHF;
        } else if (StationPackingUtils.HVHF_CHANNELS.contains(aChannel)) {
            return Band.HVHF;
        } else {
            Preconditions.checkState(StationPackingUtils.LVHF_CHANNELS.contains(aChannel));
            return Band.LVHF;
        }
    }

    /**
     * Set the UHF channels by providing a highest available UHF channel. All UHF channels will then be between 14 and the provided highest channel.
     *
     * @param aHighestChannel - the highest available UHF channel.
     */

    public static void setUHFChannels(int aHighestChannel) {
        Preconditions.checkArgument(aHighestChannel >= 14, "Channel %s is not in UHF, must be >= 14", aHighestChannel);
        UHF_CHANNELS = IntStream.rangeClosed(14, StationPackingUtils.UHFmax).filter(StationPackingUtils.UHF_CHANNELS::contains).mapToObj(Integer::valueOf).collect(toImmutableSet());
    }

}