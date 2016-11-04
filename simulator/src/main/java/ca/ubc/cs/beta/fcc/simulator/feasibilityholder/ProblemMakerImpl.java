package ca.ubc.cs.beta.fcc.simulator.feasibilityholder;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.prevassign.IPreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.ProblemType;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.fcc.simulator.state.RoundTracker;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableMap;

/**
 * Created by newmanne on 2016-08-04.
 */
@RequiredArgsConstructor
@Slf4j
public class ProblemMakerImpl implements IProblemMaker {

    @NonNull
    private final ILadder ladder;
    @NonNull
    private final Simulator.ISATFCProblemSpecGenerator problemSpecGenerator;
    @NonNull
    private final RoundTracker roundTracker;

    private int problemNumber = 0;

    @Override
    public SimulatorProblem makeProblem(IStationInfo station, Band band, ProblemType problemType, String name) {
        final ImmutableSet<IStationInfo> bandStations = ladder.getBandStations(band);
        // Add s
        final ImmutableSet<IStationInfo> sSet = ImmutableSet.of(station);
        final ImmutableSet<IStationInfo> plusS = ImmutableSet.copyOf(Sets.union(bandStations, sSet));
        return makeProblem(plusS, band, SimulatorProblem.builder().problemType(problemType).targetStation(station), name);
    }

    @Override
    public SimulatorProblem makeProblem(Set<IStationInfo> stations, Band band, ProblemType problemType, IStationInfo targetStation, String name) {
        return makeProblem(stations, band, SimulatorProblem.builder().problemType(problemType).targetStation(targetStation), name);
    }

    private SimulatorProblem makeProblem(Set<IStationInfo> stations, Band band, SimulatorProblem.SimulatorProblemBuilder builder, String name) {
        builder.band(band).round(roundTracker.getRound());
        builder.problemNumber(problemNumber++);
        final Map<Integer, Set<Integer>> domains = stations.stream().collect(toImmutableMap(
                IStationInfo::getId,
                s -> s.getDomain(band)
        ));

        final SimulatorProblemReader.SATFCProblem satfcProblem = new SimulatorProblemReader.SATFCProblem(
                domains,
                ladder.getPreviousAssignment(domains)
        );
        if (name == null) {
            final SimulatorProblem temp = builder.build();
            if (ImmutableSet.of(ProblemType.BID_PROCESSING_HOME_BAND_FEASIBLE, ProblemType.BID_PROCESSING_MOVE_FEASIBLE, ProblemType.BID_STATUS_UPDATING_HOME_BAND_FEASIBLE, ProblemType.PROVISIONAL_WINNER_CHECK, ProblemType.UHF_CACHE_PREFILL).contains(temp.getProblemType())) {
                name = Joiner.on('_').join("R" + temp.getRound(), temp.getProblemType(), temp.getTargetStation(), temp.getBand());
            } else {
                name = temp.getProblemType().toString();
            }
        }
        return builder.SATFCProblem(problemSpecGenerator.createProblem(satfcProblem, name)).build();
    }

}
