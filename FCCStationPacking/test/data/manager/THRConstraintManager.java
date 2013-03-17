package data.manager;

import java.io.IOException;
import java.util.HashSet;

import data.Constraint;
import data.Station;

import junit.framework.TestCase;

public class THRConstraintManager extends TestCase {
	
	public void testPairwiseConstraintStations() throws IOException
	{
		IConstraintManager aConstraintManager = new HRConstraintManager("/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPacking/experiment_dir/AllowedChannels.csv", "/ubc/cs/home/a/afrechet/arrow-space/workspace/FCCStationPacking/experiment_dir/PairwiseConstraints.csv");
		
		HashSet<Station> aStations = new HashSet<Station>();
		HashSet<Integer> aStationIDs = new HashSet<Integer>();
	
		for(Constraint aConstraint : aConstraintManager.getPairwiseConstraints())
		{
			
			aStations.add(aConstraint.getInterferingPair().getKey());
			aStations.add(aConstraint.getProtectedPair().getKey());
			
			aStationIDs.add(aConstraint.getInterferingPair().getKey().getID());
			aStationIDs.add(aConstraint.getProtectedPair().getKey().getID());
			
		}

		System.out.println(aStations.size());
		System.out.println(aStationIDs.size());
		assertTrue(aStations.size()==aStationIDs.size());
	}
	
}
