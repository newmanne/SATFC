package ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency;

import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
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

/**
 * Created by pcernek on 5/8/15.
 * Enforce arc consistency on the problem domains with the hope of shrinking them
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
        SATFCMetrics.postEvent(new SATFCMetrics.TimingEvent(aInstance.getName(), SATFCMetrics.TimingEvent.ARC_CONSISTENCY, watch.getElapsedTime()));
        if (ac3Output.isNoSolution()) {
            return SolverResult.createNonSATResult(SATResult.UNSAT, watch.getElapsedTime(), SolverResult.SolvedBy.ARC_CONSISTENCY);
        } else {
            log.debug("Removed {} channels", ac3Output.getNumReducedChannels());
            final StationPackingInstance reducedInstance = new StationPackingInstance(ac3Output.getReducedDomains(), aInstance.getPreviousAssignment(), aInstance.getMetadata());
            return SolverResult.relabelTime(fDecoratedSolver.solve(reducedInstance, aTerminationCriterion, aSeed), watch.getElapsedTime());
        }
    }

}