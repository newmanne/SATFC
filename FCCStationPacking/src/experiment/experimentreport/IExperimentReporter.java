package experiment.experimentreport;

import experiment.instance.IInstance;
import experiment.solver.result.SolverResult;

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
	public void report(IInstance aInstance, SolverResult aRunResult) throws Exception;
	
}
