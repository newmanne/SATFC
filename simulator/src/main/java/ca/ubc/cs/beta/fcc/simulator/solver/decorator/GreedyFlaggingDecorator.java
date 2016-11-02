package ca.ubc.cs.beta.fcc.simulator.solver.decorator;

import ca.ubc.cs.beta.aeatk.misc.cputime.CPUTime;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-11-01.
 * A (blocking) greedy solver
 */
@Slf4j
public class GreedyFlaggingDecorator extends AFeasibilitySolverDecorator {

    private final IConstraintManager constraintManager;

    private final ImmutableTable<IStationInfo, Band, Set<IStationInfo>> neighbourIndex;

    public GreedyFlaggingDecorator(@NonNull IFeasibilitySolver decorated, @NonNull ILadder ladder, @NonNull IConstraintManager constraintManager) {
        super(decorated);
        this.constraintManager = constraintManager;
        Preconditions.checkState(ladder.getStations().size() > 0);
        neighbourIndex = SimulatorUtils.getBandNeighborIndexMap(ladder, constraintManager);
    }

    @Override
    public void getFeasibility(SimulatorProblem problem, SATFCCallback callback) {
        final Watch watch = Watch.constructAutoStartWatch();
        final CPUTime cpuTime = new CPUTime();
        final IStationInfo targetStation = problem.getTargetStation();
        if (targetStation != null) {
            final Set<IStationInfo> neighbours = neighbourIndex.get(targetStation, problem.getBand());
            final Map<Integer, Set<Station>> assignment = new HashMap<>();
            final Map<Integer, Integer> previousAssignment = problem.getSATFCProblem().getProblem().getPreviousAssignment();
            for (IStationInfo neighbour : neighbours) {
                final Integer assignedChannel = previousAssignment.get(neighbour.getId());
                if (assignedChannel != null) {
                    assignment.putIfAbsent(assignedChannel, new HashSet<>());
                    assignment.get(assignedChannel).add(neighbour.toSATFCStation());
                }
            }
            // Try every channel
            for (int channel : problem.getSATFCProblem().getProblem().getDomains().get(targetStation.getId())) {
                assignment.putIfAbsent(channel, new HashSet<>());
                assignment.get(channel).add(targetStation.toSATFCStation());
                if (constraintManager.isSatisfyingAssignment(assignment)) {
                    final Map<Integer, Integer> witnessAssignment = StationPackingUtils.stationToChannelFromChannelToStation(assignment).entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().getID(), Map.Entry::getValue));
                    final SATFCResult satfcResult = new SATFCResult(SATResult.SAT, watch.getElapsedTime(), cpuTime.getCPUTime(), witnessAssignment);
                    callback.onSuccess(problem, SimulatorResult.builder().SATFCResult(satfcResult).greedySolved(true).build());
                    return;
                } else {
                    assignment.get(channel).remove(targetStation.toSATFCStation());
                }
            }
        }
        super.getFeasibility(problem, callback);
    }

}
