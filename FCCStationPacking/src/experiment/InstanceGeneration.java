package experiment;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.Station;
import experiment.experimentreport.IExperimentReporter;
import experiment.instance.IInstance;
import experiment.instanceencoder.IInstanceEncoder;
import experiment.solver.ISolver;
import experiment.solver.result.SolverResult;
import experiment.solver.result.SATResult;

/**
 * Experiment to generate largest satisfiable station packing instance.
 * @author afrechet
 *
 */
public class InstanceGeneration {

	private static Logger log = LoggerFactory.getLogger(InstanceGeneration.class);
	
	private IInstanceEncoder fInstanceEncoder;
	private ISolver fSolver;
	private IExperimentReporter fExperimentReporter;
	
	
	public InstanceGeneration(IInstanceEncoder aInstanceEncoder, ISolver aSolver, IExperimentReporter aExperimentReporter)
	{
		fInstanceEncoder = aInstanceEncoder;
		fSolver = aSolver;
		fExperimentReporter = aExperimentReporter;
	}
	
	public void run(Set<Station> aStartingStations, Iterator<Station> aStationIterator, double aCutoff)
	{
		
		HashSet<Station> aCurrentStations = new HashSet<Station>(aStartingStations);
		
		while(aStationIterator.hasNext())
		{
			
			Station aStation = aStationIterator.next();
			
			log.info("Trying to add {} to current set.",aStation);
			
			aCurrentStations.add(aStation);
			
			try 
			{
				log.info("Getting problem instance.");
				IInstance aInstance = fInstanceEncoder.getProblemInstance(aCurrentStations);
				
				
				log.info("Solving instance of size {}.",aInstance.getNumberofStations());
				
				SolverResult aRunResult = fSolver.solve(aInstance,aCutoff);
				
				log.info("Result: {}",aRunResult);
				
				try
				{
					fExperimentReporter.report(aInstance, aRunResult);
				} 
				catch (Exception e)
				{
					e.printStackTrace();
				}
				
				
				if(!aRunResult.getResult().equals(SATResult.SAT)){
					log.info("Instance was UNSAT, removing station.");
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
