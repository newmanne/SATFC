package ca.ubc.cs.beta.stationpacking.experiment;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import ca.ubc.cs.beta.aclib.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aclib.misc.options.UsageSection;
import ca.ubc.cs.beta.aclib.options.AbstractOptions;
import ca.ubc.cs.beta.aclib.options.ConfigToLaTeX;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorLoader;
import ca.ubc.cs.beta.stationpacking.datamanagers.DACStationManager;
import ca.ubc.cs.beta.stationpacking.datastructures.Station;
import ca.ubc.cs.beta.stationpacking.execution.MainSolver;
import ca.ubc.cs.beta.stationpacking.execution.parameters.InstanceGenerationParameters;
import ca.ubc.cs.beta.stationpacking.experiment.experimentreport.IExperimentReporter;
import ca.ubc.cs.beta.stationpacking.experiment.experimentreport.LocalExperimentReporter;
import ca.ubc.cs.beta.stationpacking.solver.ISolver;


public class InstanceGenerationCommandLine {
		
	private static Logger log = LoggerFactory.getLogger(InstanceGenerationCommandLine.class);

	
	/**
	 * This class is designed to take command-line parameters, create a solver,
	 * and then perform an "instance generation" run. Currently, the default parameters
	 * are not complete: they do not have the information required for a TAESolver run, and 
	 * aPaxosArgs do not have the information required for an IncrementalSolver run.
	 */
	
	
	public static void main(String[] args) throws Exception {
		
		/**
		 * Test arguments to use, instead of compiling and using command line.
		**/
		String[] aNArnostiArgs = {"-STATIONS_FILE",
				"/Users/narnosti/Documents/FCCOutput/toy_stations.txt",
				"-DOMAINS_FILE",
				"/Users/narnosti/Documents/FCCOutput/toy_domains.txt",
				"-CONSTRAINTS_FILE",
				"/Users/narnosti/Documents/FCCOutput/toy_constraints.txt",
				"-EXPERIMENT_NAME",
				"TestExperiment",
				"-EXPERIMENT_DIR",
				"/Users/narnosti/Documents/fcc-station-packing/FCCStationPacking/ExperimentDir",
				"-PACKING_CHANNELS",
				"14,15,16",
				/*
				"--algoExec",
				"python solverwrapper.py",

				"--execDir",
				"SATsolvers",
				"-SOLVER",
				"picosat",s
				*/
				"-CUTOFF",
				"20.0",
				"-SEED",
				"123",
				"-SOLVER",
				"glueminisat-incremental",
				"-LIBRARY",
				"/Users/narnosti/Documents/fcc-station-packing/FCCStationPacking/SATsolvers/glueminisat-2.2.5/core/libglueminisat.so",
				};
		
		String[] aNArnostiRealArgs = {"-STATIONS_FILE",
				"/Users/narnosti/Documents/FCCOutput/stations.csv",
				"-DOMAINS_FILE",
				"/Users/narnosti/Dropbox/Alex/2013 04 New Data/Domain 041813.csv",
				"-CONSTRAINTS_FILE",
				"/Users/narnosti/Dropbox/Alex/2013 04 New Data/Interferences 041813.csv",
				"-EXPERIMENT_NAME",
				"TestExperiment",
				"-EXPERIMENT_DIR",
				"/Users/narnosti/Documents/fcc-station-packing/FCCStationPacking/ExperimentDir",
				/*
				"-PACKING_CHANNELS",
				"14,15,16",
				"--algoExec",
				"python solverwrapper.py",
				"--cutoffTime",
				"1800",
				"--execDir",
				"SATsolvers",
				"-SOLVER",
				"picosat",s
				*/
				"-SEED",
				"123",
				"-STARTING_STATIONS",
				"24914",
				"-SOLVER",
				"glueminisat-incremental",
				"-LIBRARY",
				"/Users/narnosti/Documents/fcc-station-packing/FCCStationPacking/SATsolvers/glueminisat-2.2.5/core/libglueminisat.so",
				};
		
		
		String[] aPaxosArgs = {"-STATIONS_FILE",
				"/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir/Data/stations2.csv",
				"-DOMAINS_FILE",
				"/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir/Data/NewDACData/Domain-041813.csv",
				"-CONSTRAINTS_FILE",
				"/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir/Data/NewDACData/Interferences-041813.csv",
				"-EXPERIMENT_NAME",
				"TestExperiment",
				"-EXPERIMENT_DIR",
				"/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir/Results/TestExperiment"
				};
		
		args = aNArnostiRealArgs;
		
		InstanceGenerationParameters aExecParameters = getParameterParser(args);

	    log.info("Creating solver...");
		ISolver aSolver = new MainSolver(args);
	    
	    log.info("Creating experiment reporter...");
		IExperimentReporter aExperimentReporter = new LocalExperimentReporter(aExecParameters.getExperimentDir(), aExecParameters.getExperimentName());
			
		InstanceGeneration aInstanceGeneration = new InstanceGeneration(aSolver,aExperimentReporter);
		
		
		HashSet<Integer> aChannels = aExecParameters.getPackingChannels();
		log.info("Packing channels are "+aChannels);
		
		
		log.info("Getting station information...");
		DACStationManager aStationManager = new DACStationManager(aExecParameters.getRepackingDataParameters().StationFilename,aExecParameters.getRepackingDataParameters().DomainFilename);
	    
		
		Set<Station> aStations = aStationManager.getStations();
		HashSet<Integer> aConsideredStationIDs = aExecParameters.getConsideredStationsIDs();
		HashSet<Integer> aCurrentStationIDs = aExecParameters.getStartingStationsIDs();
		HashSet<Station> aToConsiderStations = new HashSet<Station>();
		HashSet<Station> aStartingStations = new HashSet<Station>();
		
		/*
		 * For each station we know about, if it's in the set of starting stations, 
		 * add it to current stations. If it's not in the set of already-considered stations,
		 * add it to the set of stations which we will consider in the InstanceGeneration run.
		 */
		for(Station aStation : aStations){
			if(aCurrentStationIDs.contains(aStation.getID())){
				aStartingStations.add(aStation);
			}
			if(!aConsideredStationIDs.contains(aStation.getID())){
				aToConsiderStations.add(aStation);
			}
		}
		Iterator<Station> aStationIterator = new InversePopulationStationIterator(aToConsiderStations, aExecParameters.getSeed());

		log.info("Beginning experiment...");
		aInstanceGeneration.run(aStartingStations, aStationIterator, aChannels, aExecParameters.getCutoffTime(),aExecParameters.getSeed());

	}
	
	
	/*
	 * Copied existing code to parse parameters
	 */
	private static InstanceGenerationParameters getParameterParser(String[] args){
				
		//TAE Options
		Map<String,AbstractOptions> aAvailableTAEOptions = TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators();
		
		//Parse the command line arguments in a parameter object.
		InstanceGenerationParameters aExecParameters = new InstanceGenerationParameters();
		JCommander aParameterParser = JCommanderHelper.getJCommander(aExecParameters, aAvailableTAEOptions);
		try
		{
			aParameterParser.parse(args);
		}
		catch (ParameterException aParameterException)
		{
			log.error(aParameterException.getMessage());

			List<UsageSection> sections = ConfigToLaTeX.getParameters(aExecParameters, aAvailableTAEOptions);
			
			boolean showHiddenParameters = false;
			
			//A much nicer usage screen than JCommander's 
			ConfigToLaTeX.usage(sections, showHiddenParameters);
			
		}


		return aExecParameters;
	}
}