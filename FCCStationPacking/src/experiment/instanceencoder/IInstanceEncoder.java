package experiment.instanceencoder;

import java.util.Set;


import data.Station;
import experiment.probleminstance.IProblemInstance;

public interface IInstanceEncoder {

	public IProblemInstance getProblemInstance(Set<Station> aStations) throws Exception;
	
}
