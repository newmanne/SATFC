package ca.ubc.cs.beta.stationpacking.cache;

import org.springframework.web.client.RestTemplate;

/**
 * Created by newmanne on 06/03/15.
 */
public class CacherProxy implements ICacher {

	final RestTemplate restTemplate = new RestTemplate();
	
	@Override
	public void cacheResult(CacheCoordinate cacheCoordinate, CacheEntry entry) {
		
	}

}
