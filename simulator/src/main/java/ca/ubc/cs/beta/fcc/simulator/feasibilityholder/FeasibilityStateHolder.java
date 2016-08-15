package ca.ubc.cs.beta.fcc.simulator.feasibilityholder;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.prevassign.IPreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by newmanne on 2016-08-04.
 */
@RequiredArgsConstructor
public class FeasibilityStateHolder implements IFeasibilityStateHolder {

    @NonNull
    private final IFeasibilitySolver solver;
    @NonNull
    private final IPreviousAssignmentHandler previousAssignmentHandler;
    @NonNull
    private final ILadder ladder;

    @Override
    public SATFCResult getFeasibility(IStationInfo station, Band band) {
        Set<IStationInfo> stations = new HashSet<>(ladder.getBandStations(band));
        stations.add(station);
        return solver.getFeasibilityBlocking(stations, previousAssignmentHandler.getPreviousAssignment(null));
    }

}
