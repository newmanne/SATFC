package ca.ubc.cs.beta.stationpacking.base;

import java.util.Comparator;


/**
 * Standard comparator for stations based on their IDs.
 * @author afrechet
 *
 */
public class StationComparator implements Comparator<Station> {
	
	@Override
	public int compare(Station o1, Station o2) {
		return Integer.valueOf(o1.getID()).compareTo(Integer.valueOf(o2.getID()));
	}

}