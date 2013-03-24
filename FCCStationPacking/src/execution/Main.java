package execution;

import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import data.Station;
import data.manager.HRConstraintManager;
import data.manager.HRStationManager;
import data.manager.IConstraintManager;
import data.manager.IStationManager;

import experiment.InstanceGeneration;
import experiment.experimentreport.IExperimentReporter;
import experiment.experimentreport.LocalExperimentReporter;
import experiment.instanceencoder.IInstanceEncoder;
import experiment.instanceencoder.InstanceEncoder;
import experiment.instanceencoder.cnfencoder.ICNFEncoder;
import experiment.instanceencoder.cnfencoder.StaticCNFEncoder;
import experiment.instanceencoder.cnflookup.CachedCNFLookup;
import experiment.instanceencoder.cnflookup.ICNFLookup;
import experiment.instanceencoder.componentgrouper.ConstraintGraphComponentGrouper;
import experiment.instanceencoder.componentgrouper.IComponentGrouper;
import experiment.solver.ISolver;
import experiment.solver.TAESolver;
import experiment.stationiterators.InversePopulationStationIterator;

public class Main {

	private static Logger log = LoggerFactory.getLogger(Main.class);
	
	public static void main(String[] args) throws Exception {
		
		Random aRandomizer = new Random(1);

		log.info("Getting data...");
		IStationManager aStationManager = new HRStationManager("/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir/Data/stations.csv");;
		IConstraintManager aConstraintManager = new HRConstraintManager("/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir/Data/AllowedChannels.csv", "/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir/Data/PairwiseConstraints.csv");

		log.info("Encoding data...");
		ICNFEncoder aCNFEncoder = new StaticCNFEncoder(aConstraintManager.getStationDomains(), aConstraintManager.getPairwiseConstraints());
		log.info("Making station groups...");
		IComponentGrouper aComponentGrouper = new ConstraintGraphComponentGrouper(aConstraintManager.getPairwiseConstraints());
		log.info("Creating cnf lookup...");
		ICNFLookup aCNFLookup = new CachedCNFLookup();
		
		log.info("Creating instance encoder...");
		IInstanceEncoder aInstanceEncoder = new InstanceEncoder(aCNFEncoder, aCNFLookup, aComponentGrouper, "/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir/CNFs");
		
		log.info("Creating solver...");
		String aParamConfigurationSpaceLocation = "/ubc/cs/home/a/afrechet/arrow-space/git/fcc-station-packing/FCCStationPacking/SATsolvers/sw_parameterspaces/sw_picosat.txt";
		String aAlgorithmExecutable = "python solverwrapper.py";
		String aExecDir = "/ubc/cs/home/a/afrechet/arrow-space/git/fcc-station-packing/FCCStationPacking/SATsolvers/";
		ISolver aSolver = new TAESolver(aParamConfigurationSpaceLocation, aAlgorithmExecutable, aExecDir, "CLI");
		
		log.info("Creating experiment reporter...");
		IExperimentReporter aExperimentReporter = new LocalExperimentReporter("/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPackingExperimentDir", "test");
		
		log.info("Creating instance generation experiment...");
		InstanceGeneration aInstanceGeneration = new InstanceGeneration(aInstanceEncoder, aSolver, aExperimentReporter);
		
		log.info("Creating station iterator...");
		Iterator<Station> aStationIterator = new InversePopulationStationIterator(aStationManager.getUnfixedStations(), aStationManager.getStationPopulation(), aRandomizer);
		log.info("Getting starting stations...");
		Set<Station> aStartingStations = aStationManager.getFixedStations();
		
		double aCutoff = 1800;
		aInstanceGeneration.run(aStartingStations, aStationIterator,aCutoff);
		
	
		
	}

}
