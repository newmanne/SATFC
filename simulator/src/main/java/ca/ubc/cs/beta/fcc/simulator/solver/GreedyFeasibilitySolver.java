package ca.ubc.cs.beta.fcc.simulator.solver;

import ca.ubc.cs.beta.aeatk.misc.cputime.CPUTime;
import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-06-10.
 */
public class GreedyFeasibilitySolver extends AFeasibilitySolver {

    private final IConstraintManager constraintManager;
    private final IStationManager stationManager;

    public GreedyFeasibilitySolver(Simulator.ISATFCProblemSpecGenerator problemSpecGenerator, IConstraintManager constraintManager, IStationManager stationManager) {
        super(problemSpecGenerator);
        this.constraintManager = constraintManager;
        this.stationManager = stationManager;
    }

    @Override
    protected void solve(SimulatorProblemReader.SATFCProblemSpecification problemSpecification, SATFCCallback callback) {
        Watch watch = Watch.constructAutoStartWatch();
        final CPUTime cpuTime = new CPUTime();
        final Map<Integer, Set<Integer>> domains = problemSpecification.getProblem().getDomains();
        final Map<Integer, Integer> previousAssignment = problemSpecification.getProblem().getPreviousAssignment();
        Preconditions.checkArgument(StationPackingUtils.weakVerify(stationManager, constraintManager, previousAssignment), "Greedy solver requires previous assignment to be valid!");
        final Set<Integer> unassigned = Sets.difference(domains.keySet(), previousAssignment.keySet());
        final Map<Integer, Integer> assignment = new HashMap<>(previousAssignment);
        for (Integer s: unassigned) {
            Set<Integer> domain = domains.get(s);
            for (Integer c: domain) {
                assignment.put(s, c);
                if (constraintManager.isSatisfyingAssignment(StationPackingUtils.channelToStationFromStationToChannel(assignment))) {
                    break;
                } else {
                    assignment.remove(s);
                }
            }
            if (!assignment.containsKey(s)) {
                // Greedy checker won't be able to prove SAT here...
                break;
            }
        }
        final SATFCResult result;
        if (assignment.size() == domains.size()) {
            result = new SATFCResult(SATResult.SAT, watch.getElapsedTime(), cpuTime.getCPUTime(), assignment);
        } else {
            result = new SATFCResult(SATResult.TIMEOUT, watch.getElapsedTime(), cpuTime.getCPUTime(), ImmutableMap.of());
        }
        callback.onSuccess(problemSpecification, result);
    }

    @Override
    public void close() throws Exception {

    }
}
