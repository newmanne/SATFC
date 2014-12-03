package ca.ubc.cs.beta.stationpacking;

import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacade;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.tae.SATFCTargetAlgorithmEvaluator;

public class TestDependencies {

	public static void main(String[] args) {
		
		SATFCFacadeBuilder builder = new SATFCFacadeBuilder();
		SATFCFacade facade = builder.build();
		TargetAlgorithmEvaluator tae = new SATFCTargetAlgorithmEvaluator(facade, "/Users/afrechet/Documents/git/fcc-station-packing/src/test/resources/data/021814SC3M");

	}

}
