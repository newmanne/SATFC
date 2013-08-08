package ca.ubc.cs.beta.stationpacking.solver;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;

/**
 * Solves station packing problem instance.
 * Usually a (fancy) wrapper around a SAT solver.
 * @author afrechet
 *
 */
public interface ISolver {
	
	public SolverResult solve(StationPackingInstance aInstance, double aCutoff, long aSeed);
	
	/**
	 * Ask the solver to shutdown.
	 */
	public void notifyShutdown();
	
}
