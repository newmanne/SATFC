package ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder;

import ca.ubc.cs.beta.stationpacking.base.Station;

import org.apache.commons.math3.util.Pair;

/**
 * Decodes variables to their station/channel equivalents.
 * @author afrechet
 *
 */
public interface ISATDecoder {
	
	/**
	 * @param aVariable - a SAT variable.
	 * @return - the station and channel encoded by the given SAT variable.
	 */
	public Pair<Station,Integer> decode(long aVariable);
	
}
