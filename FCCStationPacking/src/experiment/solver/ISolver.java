package experiment.solver;

import experiment.probleminstance.IProblemInstance;

/**
 * Solves station packing problem instance.
 * Usually a (fancy) wrapper around a SAT solver.
 * @author afrechet
 *
 */
public interface ISolver {
	
	public RunResult solve(IProblemInstance aInstance);
	
}
