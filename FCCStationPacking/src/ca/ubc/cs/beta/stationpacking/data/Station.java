package ca.ubc.cs.beta.stationpacking.data;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

/**
 * Container class for the station object.
 * Uniquely identified by its integer ID.
 * @author afrechet
 *
 */
public class Station {
	
	private Integer fID;
	private Integer fPop; //NA - added optional population field
	
	public Station(Integer aID)
	{
		fID = aID;
		fPop = -1;
	}
	
	public Station(Integer aID,Integer aPop){
		fID = aID;
		fPop = aPop;
	}
	
	public int getID()
	{
		return fID;
	}
	
	public int getPop(){
		return fPop;
	}
	
	@Override
	public String toString()
	{
		return "Station "+fID;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Station))
		{
			return false;
		}
		else
		{
			Station aStation = (Station) o;
			return fID.equals(aStation.fID);
		}
	}
	
	@Override
	public int hashCode(){
		return fID.hashCode();
	}
	
	public static String hashStationSet(Set<Station> aStations)
	{
		LinkedList<Station> aStationsList = new LinkedList<Station>(aStations);
		Collections.sort(aStationsList, new StationComparator());
		
		String aResult = "";
		Iterator<Station> aStationIterator = aStationsList.iterator();
		while(aStationIterator.hasNext()){
			Station aStation = aStationIterator.next();
			aResult += aStation.getID();
			if(aStationIterator.hasNext())
			{
				aResult+="-";
			}
		}
		return aResult;	
	}

}