package experiment.solver;

public class RunResult {
	
	private SATResult fResult;
	private double fRuntime;
	
	public RunResult(SATResult aResult, double aRuntime)
	{
		fResult = aResult;
		fRuntime = aRuntime;
	}
	
	public SATResult getResult(){
		return fResult;
	}
	
	public double getRuntime()
	{
		return fRuntime;
	}
	
}
