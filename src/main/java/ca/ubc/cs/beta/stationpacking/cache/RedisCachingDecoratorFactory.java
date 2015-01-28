package ca.ubc.cs.beta.stationpacking.cache;

import redis.clients.jedis.Jedis;

public class RedisCachingDecoratorFactory implements ICacherFactory {

	private final Jedis fJedis;
	
	public RedisCachingDecoratorFactory(String url, int port) {
		fJedis = new Jedis(url, port);
	}
	
	@Override
	public ICacher createrCacher() {
		return new RedisCacher(fJedis, new StationPackingInstanceHasher());
	}

}
