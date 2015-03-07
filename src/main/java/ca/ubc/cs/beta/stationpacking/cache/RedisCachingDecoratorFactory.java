package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.cache.ICacher.IContainmentCacher;
import redis.clients.jedis.Jedis;

public class RedisCachingDecoratorFactory implements ICacherFactory {

	private final Jedis fJedis;
	
	public RedisCachingDecoratorFactory(String url, int port) {
		fJedis = new Jedis(url, port);
	}
	
	@Override
	public IContainmentCacher createrCacher() {
		return new RedisCacher(fJedis);
	}

}
