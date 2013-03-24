package experiment.instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import data.Station;

public class Instance implements IInstance{

	private HashMap<HashSet<Station>,String> fStationComponentstoCNF;
	
	public Instance(Map<Set<Station>,String> aStationComponentstoCNF)
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

	@Override
	public int getNumberofStations() {
		int aNumberofStations = 0;
		for(HashSet<Station> aStationComponent : fStationComponentstoCNF.keySet())
		{
			aNumberofStations += aStationComponent.size();
		}
		return aNumberofStations;
	}

}
