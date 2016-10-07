package ca.ubc.cs.beta.fcc.simulator.ladder;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.NonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableList;
import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableSet;

/**
 * Created by newmanne on 2016-07-26.
 */
public class SimpleLadder implements IModifiableLadder {

    private final Map<IStationInfo, Band> ladder;
    private final ImmutableList<Band> bands;

    /**
     * Create a simple ladder.
     *
     * @param ladderBands - list of bands forming the ladder (order) is important.
     */
    public SimpleLadder(List<Band> ladderBands) {
        ladder = new HashMap<>();
        bands = ImmutableList.copyOf(ladderBands);
    }

    @Override
    public final ImmutableSet<IStationInfo> getStations() {
        return ImmutableSet.copyOf(ladder.keySet());
    }

    @Override
    public final ImmutableList<Band> getBands() {
        return bands;
    }

    @Override
    public final Band getStationBand(@NonNull IStationInfo station) {
        return ladder.get(station);
    }

    @Override
    public final ImmutableSet<IStationInfo> getBandStations(@NonNull Band band) {
        return ladder.entrySet().stream()
                .filter(e -> e.getValue().equals(band))
                .map(Entry::getKey)
                .collect(toImmutableSet());
    }

    @Override
    public ImmutableList<Band> getPossibleMoves(@NonNull IStationInfo station) {
        final Band band = getStationBand(station);
        return ImmutableList.copyOf(Band.values()).subList(band.ordinal(), station.getHomeBand().ordinal() + 1);
    }

    @Override
    public void addStation(IStationInfo station, Band band) {
        Preconditions.checkState(!ladder.containsKey(station), "Station %s is already in the ladder!", station);
        ladder.put(station, band);
    }

    @Override
    public void moveStation(IStationInfo station, Band band) {
        final Band currentBand = ladder.remove(station);
        Preconditions.checkNotNull(currentBand);
        Preconditions.checkState(band.isAboveOrEqualTo(currentBand));
        ladder.put(station, band);
    }

}