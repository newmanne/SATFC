package experiment.probleminstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import data.Station;
import data.StationComparator;

public class ProblemInstance implements IProblemInstance{

	private HashMap<HashSet<Station>,String> fStationComponentstoCNF;
	
	public ProblemInstance(Map<Set<Station>,String> aStationComponentstoCNF)
	{
		fStationComponentstoCNF = new HashMap<HashSet<Station>,String>();
		for(Set<Station> aStationComponent : aStationComponentstoCNF.keySet())
		{
			fStationComponentstoCNF.put(new HashSet<Station>(aStationComponent), aStationComponentstoCNF.get(aStationComponent));
		}
	}
	
	@Override
	public ArrayList<String> getCNFs() {
		return new ArrayList<String>(fStationComponentstoCNF.values());
	}
	
	@Override
	public String toString()
	{
		Set<Station> aStations = new HashSet<Station>();
		for(HashSet<Station> aStationComponent : fStationComponentstoCNF.keySet())
		{
			aStations.addAll(aStationComponent);
		}
		return Station.hashStationSet(aStations);
	}

}
