package data;

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
