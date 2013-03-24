package experiment.solver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;
import ca.ubc.cs.beta.aclib.algorithmrun.RunResult;
import ca.ubc.cs.beta.aclib.algorithmrun.kill.KillableAlgorithmRun;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.aclib.options.AbstractOptions;
import ca.ubc.cs.beta.aclib.options.TargetAlgorithmEvaluatorOptions;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.aclib.runconfig.RunConfig;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluatorBuilder;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.cli.CommandLineTargetAlgorithmEvaluatorOptions;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.currentstatus.CurrentRunStatusObserver;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.loader.TargetAlgorithmEvaluatorLoader;
import experiment.instance.IInstance;
import experiment.solver.result.SATResult;
import experiment.solver.result.SolverResult;

/**
 * SAT solver wrapper that uses Steve Ramage's AClib Target Algorithm Evaluators for execution.
 * @author afrechet
 *
 */
public class TAESolver implements ISolver{
	
	private final double fScenarioCutoff = 999999.0;
	
	private ParamConfigurationSpace fParamConfigurationSpace;
	private TargetAlgorithmEvaluator fTargetAlgorithmEvaluator;
	private int fSeed;
	
	/**
	 * Construct a solver wrapper around a target algorithm evaluator.
	 * @param aParamConfigurationSpaceFile - the location of the ParamILS formatted parameter configuration space file.
	 * @param aAlgorithmExecutable - the execution string of the algorithm executable (<i> e.g. </i> "python SATwrapper.py").
	 * @param aExecDir - the directory in which to execute the algorithm (<i> e.g. </i> "[...]/SolverWrapper/").
	 * @param aTargetAlgorithmEvaluatorExecutionEnvironment (<i> e.g. </i> "CLI" command-line on system, "MYSQLDBTAE" plug-in for mySQL workers, ...). 
	 */
	public TAESolver(String aParamConfigurationSpaceFile, String aAlgorithmExecutable, String aExecDir, String aTargetAlgorithmEvaluatorExecutionEnvironment)
	{
		//Parameter configuration space
		fParamConfigurationSpace  = new ParamConfigurationSpace(new File(aParamConfigurationSpaceFile));
		
		
		//Algorithm Execution Config
		AlgorithmExecutionConfig aAlgorithmExecutionConfig = new AlgorithmExecutionConfig(aAlgorithmExecutable, aExecDir, fParamConfigurationSpace, false, false, fScenarioCutoff);
		
		//Target Algorithm Evaluator
		TargetAlgorithmEvaluatorOptions aTargetAlgorithmEvaluatorOptions = new TargetAlgorithmEvaluatorOptions();
		aTargetAlgorithmEvaluatorOptions.retryCount = 0 ;
		aTargetAlgorithmEvaluatorOptions.maxConcurrentAlgoExecs = 3;
		aTargetAlgorithmEvaluatorOptions.targetAlgorithmEvaluator = aTargetAlgorithmEvaluatorExecutionEnvironment;
		
		Map<String,AbstractOptions> aTargetAlgorithmEvaluatorOptionsMap = TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators();
		
		//Setting CLI options.
		CommandLineTargetAlgorithmEvaluatorOptions CLIopts = (CommandLineTargetAlgorithmEvaluatorOptions) aTargetAlgorithmEvaluatorOptionsMap.get("CLI");
		CLIopts.logAllCallStrings = false;
		CLIopts.logAllProcessOutput = false;
		CLIopts.concurrentExecution = true;
		
		fTargetAlgorithmEvaluator = TargetAlgorithmEvaluatorBuilder.getTargetAlgorithmEvaluator(aTargetAlgorithmEvaluatorOptions, aAlgorithmExecutionConfig, false, aTargetAlgorithmEvaluatorOptionsMap);
		
	}
	
	
	/**
	 * Target algorithm run observer that kills runs when one UNSAT or TIMEOUT is found.
	 * @return a preempting run status observer.
	 */
	private CurrentRunStatusObserver getPreemptingObserver()
	{
		return new CurrentRunStatusObserver(){
		
			@Override
			public void currentStatus(List<? extends KillableAlgorithmRun> runs) {
				boolean aKill = false;
				for(KillableAlgorithmRun aRun : runs)
				{
					if(!aRun.getRunResult().equals(RunResult.SAT) && !aRun.getRunResult().equals(RunResult.RUNNING))
					{
						aKill = true;
						break;
					}
				}
				if(aKill)
				{
					for(KillableAlgorithmRun aRun : runs)
					{
						aRun.kill();
					}
				}
					
			}
			
		};
	}
	
	@Override
	public SolverResult solve(IInstance aInstance, double aCutoff) {
		
		
		//Create runs for TAE.
		List<RunConfig> aRunConfigList = new ArrayList<RunConfig>();
		for(String aCNFFilename : aInstance.getCNFs())
		{
			ProblemInstance aProblemInstance = new ProblemInstance(aCNFFilename);
			ProblemInstanceSeedPair aProblemInstanceSeedPair = new ProblemInstanceSeedPair(aProblemInstance,fSeed);
			RunConfig aRunConfig = new RunConfig(aProblemInstanceSeedPair, aCutoff, fParamConfigurationSpace.getDefaultConfiguration());
			aRunConfigList.add(aRunConfig);
		}
		
		List<AlgorithmRun> aRuns = fTargetAlgorithmEvaluator.evaluateRun(aRunConfigList, getPreemptingObserver());
		
		SATResult aSATResult = SATResult.SAT;
		double aRuntime = 0.0;
		for(AlgorithmRun aRun : aRuns)
		{
			aRuntime += aRun.getRuntime();
			
			if(aRun.getRunResult().equals(RunResult.UNSAT) && aSATResult.equals(SATResult.SAT))
			{
				aSATResult = SATResult.UNSAT;
			}
			else if(aRun.getRunResult().equals(RunResult.TIMEOUT) && !aSATResult.equals(SATResult.CRASHED))
			{
				aSATResult = SATResult.TIMEOUT;
			}
			else if(!aRun.getRunResult().equals(RunResult.SAT))
			{
				aSATResult = SATResult.CRASHED;
			}
		}
		
		return new SolverResult(aSATResult,aRuntime);
	}

}
