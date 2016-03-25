package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by newmanne on 19/01/16.
 */
@Slf4j
public class PreviousAssignmentContainsAnswerDecorator extends ASolverDecorator {

    private final IConstraintManager constraintManager;

    /**
     * @param aSolver - decorated ISolver.
     */
    public PreviousAssignmentContainsAnswerDecorator(ISolver aSolver, IConstraintManager constraintManager) {
        super(aSolver);
        this.constraintManager = constraintManager;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Watch watch = Watch.constructAutoStartWatch();
        final HashMultimap<Integer, Station> assignment = HashMultimap.create();
        aInstance.getPreviousAssignment().entrySet().stream().forEach(entry -> {
            if (aInstance.getStations().contains(entry.getKey())) {
                assignment.put(entry.getValue(), entry.getKey());
            }
        });
        if (aInstance.getStations().size() == assignment.size()) {
            final Map<Integer, Set<Station>> integerSetMap = Multimaps.asMap(assignment);
            if (constraintManager.isSatisfyingAssignment(integerSetMap)) {
                log.debug("Previous solution directly solves this problem");
                return new SolverResult(SATResult.SAT, watch.getElapsedTime(), integerSetMap, SolverResult.SolvedBy.PREVIOUS_ASSIGNMENT);
            }
        }
        return super.solve(aInstance, aTerminationCriterion, aSeed);
    }
}
