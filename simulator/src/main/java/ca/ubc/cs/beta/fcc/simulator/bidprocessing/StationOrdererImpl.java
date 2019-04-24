package ca.ubc.cs.beta.fcc.simulator.bidprocessing;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;

import ca.ubc.cs.beta.fcc.simulator.utils.RandomUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import java.util.*;

/**
 * Created by newmanne on 2016-07-27.
 */
public class StationOrdererImpl implements IStationOrderer {

    /**
     * Sorts the stations in query order, first by the volume-weighted difference in price offers (descending) then by their priorities (pseduo-random).
     */
    @Override
    public ImmutableList<IStationInfo> getQueryOrder(Collection<IStationInfo> stations, IPrices prices, ILadder ladder, Map<IStationInfo, Double> previousPrices) {
        // Pseudo-random numbers for tie-breaking in this round
        final List<IStationInfo> priorities = new ArrayList<>(stations);
        Collections.shuffle(priorities, RandomUtils.getRandom());

        final Ordering<IStationInfo> primary = Ordering.from(Comparator.comparingDouble(s -> (prices.getPrice(s, ladder.getStationBand(s)) - previousPrices.get(s)) / s.getVolume()));
        final Ordering<IStationInfo> secondary = Ordering.explicit(priorities);
        final Ordering<IStationInfo> compound = primary.compound(secondary);
        return ImmutableList.copyOf(compound.sortedCopy(stations));
    }

}