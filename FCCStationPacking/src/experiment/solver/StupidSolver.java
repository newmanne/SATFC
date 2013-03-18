package experiment.solver;



import java.util.Random;

import experiment.probleminstance.IProblemInstance;

/**
 * <b> FOR TESTING PURPOSE</b> <br>
 * Does not solve given problem instance - returns SAT or UNSAT with input probability after waiting
 *  a randomized amount of time around input wait time.
 * @author afrechet
 *
 */
public class StupidSolver implements ISolver{

	private double fSATprob;
	private double fWaitTime;
	
	private Random fRandomizer;
	
	public StupidSolver(double aSATprobability, double aWaitTime, int aSeed)
	{
		fSATprob = aSATprobability;
		fWaitTime = aWaitTime;
		fRandomizer = new Random(aSeed);
	}
	
	public StupidSolver(double aSATprobability, double aWaitTime)
	{
		fSATprob = aSATprobability;
		fWaitTime = aWaitTime;
		fRandomizer = new Random(1);
	}
	
	@Override
	public RunResult solve(IProblemInstance aInstance) {
		
		double aRunTime = (fWaitTime*1000 + fRandomizer.nextDouble()*2000);
		
		try 
		{
			Thread.sleep((long) aRunTime);
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
		if(fRandomizer.nextDouble()<fSATprob)
		{
			return new RunResult(SATResult.SAT, aRunTime);
		}
		else
		{
			return new RunResult(SATResult.UNSAT, aRunTime);
		}
	}

}
