package data;

import ca.ubc.cs.beta.stationpacking.data.Station;
import junit.framework.TestCase;

public class TStation extends TestCase {
	
	public void testEqual()
	{
		int aID = 23613136;
		
		Station aStation1 = new Station(aID);
		Station aStation2 = new Station(aID);
		
		assertTrue(aStation1.equals(aStation2));
	}
	
}
