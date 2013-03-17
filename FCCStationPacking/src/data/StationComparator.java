package data;

import java.util.Comparator;

public class StationComparator implements Comparator<Station> {

	public StationComparator()
	{
		
	}
	
	@Override
	public int compare(Station o1, Station o2) {
		return Integer.compare(o1.getID(),o2.getID());
	}

}
