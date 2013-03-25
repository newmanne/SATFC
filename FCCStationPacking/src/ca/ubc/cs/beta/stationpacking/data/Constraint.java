package ca.ubc.cs.beta.stationpacking.data;

import org.apache.commons.math3.util.Pair;

/**
 * Container class for a pairwise station packing constraint. 
 * Both protected and interfering station-channel pairs cannot occur at the same time.
 * @author afrechet
 *
 */
public class Constraint {
	
	StationChannelPair fProtected;
	StationChannelPair fInterfering;
	
	public Constraint(StationChannelPair aProtected, StationChannelPair aInterfering)
	{
		fProtected = aProtected;
		fInterfering = aInterfering;
	}
	
	public StationChannelPair getProtectedPair()
	{
		return fProtected;
	}
	
	public StationChannelPair getInterferingPair()
	{
		return fInterfering;
	}
	
	@Override
	public String toString()
	{
		return fProtected.toString()+" "+fInterfering;
	}

}
