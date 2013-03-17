package experiment.experimentreport;

import experiment.probleminstance.IProblemInstance;
import experiment.solver.RunResult;

public interface IExperimentReporter {

	public boolean report(IProblemInstance aInstance, RunResult aRunResult) throws Exception;
	
}
