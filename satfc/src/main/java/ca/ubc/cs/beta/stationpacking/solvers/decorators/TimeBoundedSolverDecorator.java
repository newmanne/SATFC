package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.composite.DisjunctiveCompositeTerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.walltime.WalltimeTerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

/**
 * Created by newmanne on 15/10/15.
 * Run a solver for a fixed amount of time, then continue down the decorator tree if it fails
 */
public class TimeBoundedSolverDecorator extends ASolverDecorator {

    private final ISolver timeBoundedSolver;
    private final double timeBound;

    public TimeBoundedSolverDecorator(ISolver solverToDecorate, ISolver timeBoundedSolver, double timeBound) {
        super(solverToDecorate);
        this.timeBoundedSolver = timeBoundedSolver;
        this.timeBound = timeBound;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Watch watch = Watch.constructAutoStartWatch();
        final ITerminationCriterion newCriterion = new DisjunctiveCompositeTerminationCriterion(aTerminationCriterion, new WalltimeTerminationCriterion(timeBound));
        final SolverResult solve = timeBoundedSolver.solve(aInstance, newCriterion, aSeed);
        if (aTerminationCriterion.hasToStop() || solve.isConclusive()) {
            return solve;
        } else {
            return SolverResult.relabelTime(super.solve(aInstance, aTerminationCriterion, aSeed), watch.getElapsedTime());
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
        timeBoundedSolver.interrupt();
    }
}
