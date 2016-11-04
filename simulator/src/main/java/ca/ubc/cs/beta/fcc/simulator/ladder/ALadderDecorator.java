package ca.ubc.cs.beta.fcc.simulator.ladder;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-10-11.
 */
@RequiredArgsConstructor
public class ALadderDecorator implements IModifiableLadder {

    protected final IModifiableLadder decorated;

    @Override
    public void addStation(IStationInfo s, Band b) {
        decorated.addStation(s, b);
    }

    @Override
    public void moveStation(IStationInfo station, Band band, Map<Integer, Integer> assignment) {
        decorated.moveStation(station, band, assignment);
    }

    @Override
    public ImmutableSet<IStationInfo> getStations() {
        return decorated.getStations();
    }

    @Override
    public ImmutableList<Band> getBands() {
        return decorated.getBands();
    }

    @Override
    public Band getStationBand(IStationInfo aStation) {
        return decorated.getStationBand(aStation);
    }

    @Override
    public ImmutableSet<IStationInfo> getBandStations(Band aBand) {
        return decorated.getBandStations(aBand);
    }

    @Override
    public ImmutableList<Band> getPossibleMoves(IStationInfo aStation) {
        return decorated.getPossibleMoves(aStation);
    }

    @Override
    public Map<Integer, Integer> getPreviousAssignment() {
        return decorated.getPreviousAssignment();
    }

    @Override
    public Map<Integer, Integer> getPreviousAssignment(Map<Integer, Set<Integer>> domains) {
        return decorated.getPreviousAssignment(domains);
    }

    @Override
    public void updatePreviousAssignment(Map<Integer, Integer> previousAssignment) {
        decorated.updatePreviousAssignment(previousAssignment);
    }
}
