package experiment.experimentreport;

import experiment.probleminstance.IProblemInstance;
import experiment.solver.RunResult;

/**
 * Report and record experiment results.
 * @author afrechet
 *
 */
public interface IExperimentReporter {
	
	/**
	 * Record an instance-run result pair.
	 * @param aInstance - a problem instance.
	 * @param aRunResult - the result of solving the given instance.
	 * @throws Exception
	 */
	public void report(IProblemInstance aInstance, RunResult aRunResult) throws Exception;
	
}
