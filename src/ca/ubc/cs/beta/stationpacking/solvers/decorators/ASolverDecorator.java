package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

public abstract class ASolverDecorator implements ISolver{
	
	protected final ISolver fDecoratedSolver;
	
	public ASolverDecorator(ISolver aSolver)
	{
		fDecoratedSolver = aSolver;
	}
	
	@Override
	public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed)
	{
		return fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
	}
	
	@Override
	public void interrupt() throws UnsupportedOperationException
	{
		fDecoratedSolver.interrupt();
	}

	@Override
	public void notifyShutdown()
	{
		fDecoratedSolver.interrupt();
	}
	
}