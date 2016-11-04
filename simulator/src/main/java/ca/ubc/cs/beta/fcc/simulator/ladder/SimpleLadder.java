package ca.ubc.cs.beta.fcc.simulator.ladder;

import ca.ubc.cs.beta.fcc.simulator.prevassign.IPreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.Map.Entry;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableList;
import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableSet;

/**
 * Created by newmanne on 2016-07-26.
 */
@Slf4j
public class SimpleLadder implements IModifiableLadder {

    private final Map<IStationInfo, Band> ladder;
    private final ImmutableList<Band> bands;
    private final IPreviousAssignmentHandler previousAssignmentHandler;

    /**
     * Create a simple ladder.
     *
     * @param ladderBands - list of bands forming the ladder (order) is important.
     */
    public SimpleLadder(@NonNull List<Band> ladderBands, @NonNull IPreviousAssignmentHandler previousAssignmentHandler) {
        ladder = new HashMap<>();
        bands = ImmutableList.copyOf(ladderBands);
        this.previousAssignmentHandler = previousAssignmentHandler;
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
    public void moveStation(IStationInfo station, Band band, Map<Integer, Integer> assignment) {
        final Band currentBand = ladder.remove(station);
        Preconditions.checkNotNull(currentBand);
        Preconditions.checkState(band.isAboveOrEqualTo(currentBand));
        ladder.put(station, band);
        previousAssignmentHandler.updatePreviousAssignment(assignment);
        // Verify previous assignment state is consistent with the ladder
        for (IStationInfo ladderStation : getStations()) {
            final Band ladderBand = getStationBand(ladderStation);
            if (ladderBand.isAirBand()) {
                int assignedChannel = getPreviousAssignment().get(ladderStation.getId());
                Preconditions.checkState(BandHelper.toBand(assignedChannel).equals(ladderBand), "Station %s is on channel %s but ladder says is on band %s", ladderStation, assignedChannel, ladderBand);
            }
        }
        log.info("Moved {} from {} to {}", station, currentBand, band);
    }

    @Override
    public Map<Integer, Integer> getPreviousAssignment() {
        return previousAssignmentHandler.getPreviousAssignment();
    }

    @Override
    public Map<Integer, Integer> getPreviousAssignment(Map<Integer, Set<Integer>> domains) {
        return previousAssignmentHandler.getPreviousAssignment(domains);
    }

    @Override
    public void updatePreviousAssignment(Map<Integer, Integer> previousAssignment) {
        // Obviously bad design...
        throw new IllegalArgumentException("Don't call this method, use moveStation");
    }

}