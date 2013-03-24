package experiment.solver;

import experiment.instance.IInstance;
import experiment.solver.result.SolverResult;

/**
 * Solves station packing problem instance.
 * Usually a (fancy) wrapper around a SAT solver.
 * @author afrechet
 *
 */
public interface ISolver {
	
	public SolverResult solve(IInstance aInstance, double aCutoff);
	
}
