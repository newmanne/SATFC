package experiment.solver;

import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import experiment.probleminstance.IProblemInstance;

public class TAESolver implements ISolver{

	private TargetAlgorithmEvaluator fTAE;
	
	public TAESolver(TargetAlgorithmEvaluator aTargetAlgorithmEvaluator)
	{
		fTAE = aTargetAlgorithmEvaluator;
	}
	
	@Override
	public RunResult solve(IProblemInstance aInstance) {
		
		for(String aCNFFilename : aInstance.getCNFs())
		{
			// TODO Use fTAE to solve each CNF.
		}
		

		return null;
	}

}
