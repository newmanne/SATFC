package execution;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

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
import experiment.solver.StupidSolver;
import experiment.stationiterators.InversePopulationStationIterator;

public class Main {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		
		Random aRandomizer = new Random(1);

		System.out.println("Getting data...");
		IStationManager aStationManager = new HRStationManager("/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPacking/experiment_dir/stations.csv");;
		IConstraintManager aConstraintManager = new HRConstraintManager("/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPacking/experiment_dir/AllowedChannels.csv", "/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPacking/experiment_dir/PairwiseConstraints.csv");

		System.out.println("Encoding data...");
		ICNFEncoder aCNFEncoder = new StaticCNFEncoder(aConstraintManager.getStationDomains(), aConstraintManager.getPairwiseConstraints());
		System.out.println("Making station groups...");
		IComponentGrouper aComponentGrouper = new ConstraintGraphComponentGrouper(aConstraintManager.getPairwiseConstraints());
		System.out.println("Creating cnf lookup...");
		ICNFLookup aCNFLookup = new CachedCNFLookup();
		
		System.out.println("Creating instance encoder...");
		IInstanceEncoder aInstanceEncoder = new InstanceEncoder(aCNFEncoder, aCNFLookup, aComponentGrouper, "/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPacking/experiment_dir");
		
		System.out.println("Creating stupid solver...");
		ISolver aSolver = new StupidSolver(0.5, 1);
		System.out.println("Creating experiment reporter...");
		IExperimentReporter aExperimentReporter = new LocalExperimentReporter("/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPacking/experiment_dir", "test");
		
		System.out.println("Creating instance generation experiment...");
		InstanceGeneration aInstanceGeneration = new InstanceGeneration(aInstanceEncoder, aSolver, aExperimentReporter);
		
		System.out.println("Creating station iterator...");
		Iterator<Station> aStationIterator = new InversePopulationStationIterator(aStationManager.getUnfixedStations(), aStationManager.getStationPopulation(), aRandomizer);
		System.out.println("Getting starting stations...");
		Set<Station> aStartingStations = aStationManager.getFixedStations();
		
	aInstanceGeneration.run(aStartingStations, aStationIterator);
		
	
		
	}

}
