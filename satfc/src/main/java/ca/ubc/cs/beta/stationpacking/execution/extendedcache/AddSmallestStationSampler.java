package ca.ubc.cs.beta.stationpacking.execution.extendedcache;

import java.util.Set;

import ca.ubc.cs.beta.stationpacking.base.Station;

import com.google.common.collect.Sets;

/**
 * Created by emily404 on 6/10/15.
 */
public class AddSmallestStationSampler implements IStationSampler {

    /**
     * Find all stations that are not present in current problem, return the first one
     */
	@Override
	public Station sample(Set<Station> allStations, Set<Station> stationsInProblem) {
		return Sets.difference(allStations, stationsInProblem).iterator().next();
	}
	
}
