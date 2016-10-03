package ca.ubc.cs.beta.fcc.simulator.bidprocessing;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;

import ca.ubc.cs.beta.fcc.simulator.utils.RandomUtils;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by newmanne on 2016-07-27.
 */
public class StationOrdererImpl implements IStationOrderer {

    /**
     * Sorts the stations in query order, first by off air compensation (descending) then by their priorities (pseduo-random).
     */
    @Override
    public List<IStationInfo> getQueryOrder(Collection<IStationInfo> stations, IPrices prices, ILadder ladder) {
        // Pseudo-random numbers for tie-breaking in this round
        final List<IStationInfo> priorities = new ArrayList<>(stations);
        Collections.shuffle(priorities, RandomUtils.getRandom());

        final List<IStationInfo> orderedStations = Lists.newArrayList(stations);
        orderedStations.sort((o1, o2) -> {
            // TODO: this is wrong
            // TODO: watch for implicit floating point comparison (tie breaking)
            final double s1 = prices.getPrice(o1, Band.OFF);
            final double s2 = prices.getPrice(o2, Band.OFF);
            int totalSavingsComparisonResult = -Double.compare(s1,  s2); // minus sign for descending order
            return totalSavingsComparisonResult != 0 ? totalSavingsComparisonResult : Integer.compare(priorities.indexOf(o1), priorities.indexOf(o2));
        });
        return orderedStations;
    }

}