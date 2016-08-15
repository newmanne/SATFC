package ca.ubc.cs.beta.fcc.simulator.bidprocessing;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.prices.Prices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;

import ca.ubc.cs.beta.fcc.simulator.utils.RandomUtils;
import com.google.common.collect.ImmutableList;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-07-27.
 */
public class StationOrdererImpl implements IStationOrderer {

    // priority ordering for tie breaking in bid processing
    private final ImmutableList<IStationInfo> priorities;

    public StationOrdererImpl(Collection<IStationInfo> stations) {
        // TODO: is this supposed to change each round?
        final List<IStationInfo> tempPriorities = new ArrayList<>(stations);
        Collections.shuffle(tempPriorities, RandomUtils.getRandom());
        priorities = ImmutableList.copyOf(tempPriorities);
    }

    /**
     * Sorts the stations in query order, first by off air compensation (descending) then by their priorities (pseduo-random).
     */
    @Override
    public ImmutableList<IStationInfo> getQueryOrder(Collection<IStationInfo> stations, Prices prices, ILadder ladder) {
        final List<IStationInfo> orderedStations = priorities.stream().filter(stations::contains).collect(Collectors.toList());
        orderedStations.sort((o1, o2) -> {
            // TODO: this is wrong
            final double s1 = prices.getPrice(o1, Band.OFF);
            final double s2 = prices.getPrice(o2, Band.OFF);
            int totalSavingsComparisonResult = -Double.compare(s1,  s2); // minus sign for descending order
            return totalSavingsComparisonResult != 0 ? totalSavingsComparisonResult : Integer.compare(priorities.indexOf(o1), priorities.indexOf(o2));
        });
        return ImmutableList.copyOf(orderedStations);
    }

}