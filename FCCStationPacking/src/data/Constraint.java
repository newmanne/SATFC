package data;

import org.apache.commons.math3.util.Pair;

/**
 * Container class for a pairwise station packing constraint. 
 * Both protected and interfering station-channel pairs cannot occur at the same time.
 * @author afrechet
 *
 */
public class Constraint {
	
	Pair<Station,Integer> fProtected;
	Pair<Station,Integer> fInterfering;
	
	public Constraint(Pair<Station,Integer> aProtected, Pair<Station,Integer> aInterfering)
	{
		fProtected = aProtected;
		fInterfering = aInterfering;
	}
	
	public Pair<Station,Integer> getProtectedPair()
	{
		return fProtected;
	}
	
	public Pair<Station,Integer> getInterferingPair()
	{
		return fInterfering;
	}
	

}
