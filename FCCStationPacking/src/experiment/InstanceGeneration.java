package experiment;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;




import data.Station;
import experiment.experimentreport.IExperimentReporter;
import experiment.instanceencoder.IInstanceEncoder;
import experiment.probleminstance.IProblemInstance;
import experiment.solver.ISolver;
import experiment.solver.RunResult;
import experiment.solver.SATResult;


public class InstanceGeneration {

	private IInstanceEncoder fInstanceEncoder;
	private ISolver fSolver;
	private IExperimentReporter fExperimentReporter;
	
	
	public InstanceGeneration(IInstanceEncoder aInstanceEncoder, ISolver aSolver, IExperimentReporter aExperimentReporter)
	{
		fInstanceEncoder = aInstanceEncoder;
		fSolver = aSolver;
		fExperimentReporter = aExperimentReporter;
	}
	
	public void run(Set<Station> aStartingStations, Iterator<Station> aStationIterator)
	{
		
		HashSet<Station> aCurrentStations = new HashSet<Station>(aStartingStations);
		
		while(aStationIterator.hasNext())
		{
			
			Station aStation = aStationIterator.next();
			
			System.out.println("Trying "+aStation);
			
			aCurrentStations.add(aStation);
			
			try 
			{
				System.out.println("Getting instance...");
				IProblemInstance aInstance = fInstanceEncoder.getProblemInstance(aCurrentStations);
				
				System.out.println("Solving instance...");
				RunResult aRunResult = fSolver.solve(aInstance);
				
				try
				{
					fExperimentReporter.report(aInstance, aRunResult);
				} 
				catch (Exception e)
				{
					e.printStackTrace();
				}
				
				if(!aRunResult.equals(SATResult.SAT)){
					aCurrentStations.remove(aStation);
				}			
				
			} 
			catch (Exception e1) 
			{
				e1.printStackTrace();
			}
			
			
		}
	}
	
}
