package data.manager;

import java.util.Map;
import java.util.Set;

import data.Constraint;
import data.Station;

public interface IConstraintManager {
	
	public Set<Constraint> getPairwiseConstraints();
	
	public Map<Station,Set<Integer>> getStationDomains();
	
}
