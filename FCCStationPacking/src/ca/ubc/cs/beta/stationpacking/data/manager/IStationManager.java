package ca.ubc.cs.beta.stationpacking.data.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import ca.ubc.cs.beta.stationpacking.data.Station;


public interface IStationManager {

	public HashSet<Station> getStations();
	
	public HashSet<Station> getFixedStations();
	
	public HashSet<Station> getUnfixedStations();
	
	public HashMap<Station,Integer> getStationPopulation();
}
