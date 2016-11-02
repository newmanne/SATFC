package ca.ubc.cs.beta.stationpacking.solvers.decorators.greedy;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-06-15.
 */
public class GreedySolverDecorator extends ASolverDecorator {

    private final IConstraintManager constraintManager;
    private final IStationManager stationManager;

    /**
     * @param aSolver - decorated ISolver.
     */
    public GreedySolverDecorator(ISolver aSolver, IConstraintManager constraintManager, IStationManager stationManager) {
        super(aSolver);
        this.constraintManager = constraintManager;
        this.stationManager = stationManager;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        Watch watch = Watch.constructAutoStartWatch();
        final Map<Station, Set<Integer>> domains = aInstance.getDomains();
        final Map<Station, Integer> previousAssignment = aInstance.getPreviousAssignment();
        final Map<Integer, Integer> intPrevAssignment = previousAssignment.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getID(), Map.Entry::getValue));
        Preconditions.checkArgument(StationPackingUtils.weakVerify(stationManager, constraintManager, intPrevAssignment), "Greedy solver requires previous assignment to be valid!");
        final Set<Station> unassigned = Sets.difference(domains.keySet(), previousAssignment.keySet());

        final HashMultimap<Integer, Station> assignment = HashMultimap.create();
        for (Map.Entry<Station, Integer> entry : previousAssignment.entrySet()) {
            assignment.put(entry.getValue(), entry.getKey());
        }
        for (Station s: unassigned) {
            boolean foundChannel = false;
            Set<Integer> domain = domains.get(s);
            for (Integer c: domain) {
                assignment.put(c, s);
                if (constraintManager.isSatisfyingAssignment(Multimaps.asMap(assignment))) {
                    foundChannel = true;
                    break;
                } else {
                    assignment.remove(c, s);
                }
            }
            if (!foundChannel) {
                // Greedy checker won't be able to prove SAT here...
                break;
            }
        }
        if (assignment.size() == domains.size()) {
            return new SolverResult(SATResult.SAT, watch.getElapsedTime(), Multimaps.asMap(assignment), SolverResult.SolvedBy.GREEDY);
        } else {
            return super.solve(aInstance, aTerminationCriterion, aSeed);
        }
    }

}
