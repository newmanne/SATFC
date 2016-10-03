package ca.ubc.cs.beta.fcc.simulator.feasibilityholder;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.prevassign.IPreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableMap;

/**
 * Created by newmanne on 2016-08-04.
 */
@RequiredArgsConstructor
public class FeasibilityStateHolder implements IFeasibilityStateHolder {

    @NonNull
    private final IPreviousAssignmentHandler previousAssignmentHandler;
    @NonNull
    private final ILadder ladder;
    @NonNull
    private final Simulator.ISATFCProblemSpecGenerator problemSpecGenerator;

    @Override
    public SimulatorProblemReader.SATFCProblemSpecification makeProblem(IStationInfo station, Band band) {
        final ImmutableSet<IStationInfo> bandStations = ladder.getBandStations(band);
        // Add s
        final ImmutableSet<IStationInfo> sSet = ImmutableSet.of(station);
        final ImmutableSet<IStationInfo> plusS = ImmutableSet.copyOf(Sets.union(bandStations, sSet));
        return makeProblem(plusS, band);
    }

    public SimulatorProblemReader.SATFCProblemSpecification makeProblem(Set<IStationInfo> stations, Set<Band> bands) {
        final Map<Integer, Set<Integer>> domains = stations.stream().collect(toImmutableMap(
                IStationInfo::getId,
                s -> s.getDomain(bands)
        ));
        final SimulatorProblemReader.SATFCProblem satfcProblem = new SimulatorProblemReader.SATFCProblem(
                domains,
                previousAssignmentHandler.getPreviousAssignment(domains)
        );
        // TODO: add name. This is a relatively convenient spot in terms of knowing info, except for round
        return problemSpecGenerator.createProblem(satfcProblem,  "TODO :)");
    }

}
