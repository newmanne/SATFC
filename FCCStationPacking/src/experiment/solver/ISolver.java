package experiment.solver;

import experiment.probleminstance.IProblemInstance;


public interface ISolver {
	
	public RunResult solve(IProblemInstance aInstance);
	
}
