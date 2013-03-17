package experiment.stationiterators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;

import data.Station;


public class InversePopulationStationIterator implements Iterator<Station>{

	private Iterator<Station> fOrderedStationsIterator;
	
	public InversePopulationStationIterator(Collection<Station> aStations, Map<Station,Integer> aStationPopulation, Random aRandomizer)
	{
		
		aStations = new LinkedList<Station>(aStations);
		
		int aPopulationSum = 0;
		for(Station aStation : aStations)
		{
			aPopulationSum += aStationPopulation.get(aStation);
		}
		
		ArrayList<Station> aOrderedStations = new ArrayList<Station>();
		while(!aStations.isEmpty())
		{
			double aCandidateAggregatePopulation = aRandomizer.nextDouble()*aPopulationSum;
			
			Iterator<Station> aStationIterator = aStations.iterator();
			while(aStationIterator.hasNext())
			{
				Station aCandidateStation = aStationIterator.next();
				if(aStationPopulation.get(aCandidateStation) >= aCandidateAggregatePopulation)
				{
					aStationIterator.remove();
					aPopulationSum -= aStationPopulation.get(aCandidateStation);
					aOrderedStations.add(aCandidateStation);
					break;
				}
				aCandidateAggregatePopulation -= aStationPopulation.get(aCandidateStation);
			}		
		}
		
		fOrderedStationsIterator = aOrderedStations.iterator();
		
	}
	
	@Override
	public boolean hasNext() {
		return fOrderedStationsIterator.hasNext();
	}

	@Override
	public Station next() {
		return fOrderedStationsIterator.next();
	}

	@Override
	public void remove() {
		fOrderedStationsIterator.remove();
	}
	

}
