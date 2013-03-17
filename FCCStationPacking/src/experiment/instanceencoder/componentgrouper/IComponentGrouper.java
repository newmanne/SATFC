package experiment.instanceencoder.componentgrouper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import data.Station;

public interface IComponentGrouper {
	
	public ArrayList<HashSet<Station>> group(Set<Station> aStations);
	
	
}
