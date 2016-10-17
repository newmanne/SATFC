package ca.ubc.cs.beta.fcc.simulator.utils;

import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableList;

/**
 * Created by newmanne on 2016-07-26.
 */
public enum Band implements Comparable<Band> {

    OFF,
    LVHF,
    HVHF,
    UHF;

    public List<Band> getBandsBelowInclusive(boolean ascendingOrder) {
        final List<Band> bands = Arrays.stream(Band.values())
                .filter(b -> b.isBelowOrEqualTo(this))
                .sorted()
                .collect(Collectors.toList());
        if (!ascendingOrder) {
            bands.sort(Comparator.<Band>naturalOrder().reversed());
        }
        return bands;
    }

    public List<Band> getBandsBelowInclusive() {
        return getBandsBelowInclusive(true);
    }

    public boolean isVHF() {
        return this == LVHF || this == HVHF;
    }

    public ImmutableList<Band> getBandsAbove() {
        return Arrays.stream(Band.values()).filter(b -> b.ordinal() > ordinal()).collect(toImmutableList());
    }

    public boolean isBelow(Band other) {
        return ordinal() < other.ordinal();
    }

    public boolean isBelowOrEqualTo(Band other) { return ordinal() <= other.ordinal(); }
    public boolean isAbove(Band other) { return  ordinal() > other.ordinal(); }
    public boolean isAboveOrEqualTo(Band other) { return  ordinal() >= other.ordinal(); }

}