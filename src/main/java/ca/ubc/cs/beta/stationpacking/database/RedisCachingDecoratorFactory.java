package ca.ubc.cs.beta.stationpacking.database;

import redis.clients.jedis.Jedis;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.RedisCachingSolverDecorator;

import com.google.common.net.HostAndPort;

public class RedisCachingDecoratorFactory implements CachingDecoratorFactory {

	private final Jedis fJedis;
	
	public RedisCachingDecoratorFactory(String url, int port) {
		fJedis = new Jedis(url, port);
	}
	
	@Override
	public ISolver createCachingDecorator(ISolver aSolver, String aIntereference) {
		return new RedisCachingSolverDecorator(aSolver, aIntereference, fJedis);
	}

}
