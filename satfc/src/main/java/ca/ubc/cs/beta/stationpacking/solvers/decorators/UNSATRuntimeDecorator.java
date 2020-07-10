package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

/**
 * Created by newmanne on 2019-01-16.
 */
public class UNSATRuntimeDecorator extends ASolverDecorator {
    /**
     * @param aSolver - decorated ISolver.
     */
    public UNSATRuntimeDecorator(ISolver aSolver) {
        super(aSolver);
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final double cutoff = aTerminationCriterion.getRemainingTime();
        final SolverResult result = super.solve(aInstance, aTerminationCriterion, aSeed);
        if (result.getResult().equals(SATResult.UNSAT)) {
            return SolverResult.createNonSATResult(SATResult.UNSAT, cutoff, SolverResult.SolvedBy.UNKNOWN);
        } else {
            return SolverResult.createNonSATResult(SATResult.TIMEOUT, cutoff, SolverResult.SolvedBy.UNSOLVED);
        }
    }

}
