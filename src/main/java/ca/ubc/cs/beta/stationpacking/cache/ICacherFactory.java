package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.cache.ICacher.IContainmentCacher;

public interface ICacherFactory {
	
	IContainmentCacher createrCacher();

}
