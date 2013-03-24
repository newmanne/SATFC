package experiment.instanceencoder;

import java.util.Set;


import data.Station;
import experiment.instance.IInstance;

/**
 * Encodes a set of stations in a problem instance.
 * All constraints are given in constructor.
 * @author afrechet
 *
 */
public interface IInstanceEncoder {
	
	/**
	 * Encode a set of stations in a problem instance.
	 * @param aStations - a set of stations.
	 * @return A problem instance corresponding to a station packing problem induced by the input station set.
	 * @throws Exception
	 */
	public IInstance getProblemInstance(Set<Station> aStations) throws Exception;
	
}
