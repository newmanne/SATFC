package ca.ubc.cs.beta.stationpacking.database;

import lombok.NonNull;
import redis.clients.jedis.Jedis;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.RedisCacher;

import com.google.common.net.HostAndPort;

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
