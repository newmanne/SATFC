package ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.consistency.AC3Enforcer;
import ca.ubc.cs.beta.stationpacking.consistency.AC3Output;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by pcernek on 5/8/15.
 */
@Slf4j
public class ArcConsistencyEnforcerDecorator extends ASolverDecorator {

    private final AC3Enforcer ac3Enforcer;

    /**
     * @param aSolver           - decorated ISolver.
     * @param constraintManager
     */
    public ArcConsistencyEnforcerDecorator(ISolver aSolver, IConstraintManager constraintManager) {
        super(aSolver);
        ac3Enforcer = new AC3Enforcer(constraintManager);
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Watch watch = Watch.constructAutoStartWatch();
        final AC3Output ac3Output = ac3Enforcer.AC3(aInstance, aTerminationCriterion);
        if (ac3Output.isTimedOut()) {
            return SolverResult.createTimeoutResult(watch.getElapsedTime());
        }
        else if (ac3Output.isNoSolution()) {
            return new SolverResult(SATResult.UNSAT, watch.getElapsedTime());
        } else {
            // Remove any previous assignments that are no longer on their domains!
            final ImmutableMap<Station, Integer> reducedAssignment = aInstance.getPreviousAssignment().entrySet().stream().filter(entry -> ac3Output.getReducedDomains().get(entry.getKey()).contains(entry.getValue())).collect(GuavaCollectors.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
            final StationPackingInstance reducedInstance = new StationPackingInstance(ac3Output.getReducedDomains(), reducedAssignment, aInstance.getMetadata());
            final SolverResult solve = fDecoratedSolver.solve(reducedInstance, aTerminationCriterion, aSeed);
            return new SolverResult(solve.getResult(), watch.getElapsedTime(), solve.getAssignment());
        }
    }

}