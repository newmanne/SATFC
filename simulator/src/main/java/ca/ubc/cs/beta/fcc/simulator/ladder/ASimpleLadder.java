package ca.ubc.cs.beta.fcc.simulator.ladder;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableSet;

/**
 * Created by newmanne on 2016-07-26.
 */
public abstract class ASimpleLadder implements IModifiableLadder {

    //Ladder specific object.
    private final Map<IStationInfo, Band> ladder;

    /**
     * Create a simple ladder.
     *
     * @param aLadderBands - list of bands forming the ladder (order) is important.
     */
    public ASimpleLadder(List<Band> aLadderBands) {
        ladder = new HashMap<>();
    }

    /**
     * Identify valid moves.
     *
     * @param station - a ladder station.
     * @param band    - a target band to move to.
     * @return true if the given station can move to the given band, false otherwise.
     */
    protected abstract boolean isValidMove(IStationInfo station, Band band);

    @Override
    public final ImmutableSet<IStationInfo> getStations() {
        return ImmutableSet.copyOf(ladder.keySet());
    }

    @Override
    public final ImmutableList<Band> getBands() {
        return ImmutableList.copyOf(ladder.values());
    }

    @Override
    public final Band getStationBand(IStationInfo aStation) {
        return ladder.get(aStation);
    }

    @Override
    public final ImmutableSet<IStationInfo> getBandStations(Band aBand) {
        return ladder.entrySet().stream().filter(e -> e.getValue().equals(aBand)).map(Entry::getKey).collect(toImmutableSet());
    }

    @Override
    public final void addStations(Map<IStationInfo, Band> aStationBands) {
        //Add the station on the band.
        for (final Entry<IStationInfo, Band> entry : aStationBands.entrySet()) {
            final IStationInfo station = entry.getKey();
            final Band band = entry.getValue();
            putStation(station, band);
        }
    }

    @Override
    public final void moveStations(Map<IStationInfo, Band> aMoves) {

        //For every move,
        for (Entry<IStationInfo, Band> move : aMoves.entrySet()) {
            IStationInfo station = move.getKey();
            Band band = move.getValue();

            //Make sure its a feasible move.
            if (!isValidMove(station, band)) {
                throw new IllegalArgumentException("Required a ladder move for station " + station + " to band " + band + " that is invalid.");
            }

            //Skip moves that do not move anything.
            if (band.equals(getStationBand(station))) {
                continue;
            }

            //Execute the move.
            removeStation(station);
            putStation(station, band);
        }
    }

    /**
     * @param station - a ladder station.
     * @return the ladder rung at which the station can be found.
     */
    protected int getRung(IStationInfo station) {
        return ladder.get(station).ordinal();
    }

    /**
     * Ladder modification methods.
     */
    private void removeStation(IStationInfo station) {
        final Band removed = ladder.remove(station);
        Preconditions.checkNotNull(removed);
    }

    private void putStation(IStationInfo station, Band band) {
        Preconditions.checkState(!ladder.get(station).equals(band), "Station %s already associated with band %s on the ladder.", station, band);
    }

}