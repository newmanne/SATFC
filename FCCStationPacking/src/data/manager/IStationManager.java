package data.manager;

import java.util.HashMap;
import java.util.Set;

import data.Station;

public interface IStationManager {

	public Set<Station> getStations();
	
	public Set<Station> getFixedStations();
	
	public Set<Station> getUnfixedStations();
	
	public HashMap<Station,Integer> getStationPopulation();
}
