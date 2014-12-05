package ca.ubc.cs.beta.stationpacking.execution.parameters;

import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ACachingSolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.RedisCachingSolverDecorator;
import com.beust.jcommander.Parameter;
import lombok.Getter;
import redis.clients.jedis.Jedis;

/**
 * Created by newmanne on 04/12/14.
 */
public class SATFCCachingParameters {

    // smallest amount of time a computation can take to make it worth caching
    @Getter
    public double minTimeToCache = 1;

    @Parameter(names = "--useCache", description = "Should the cache be used", required = false)
    public boolean useCache = false;

    @Parameter(names = "--redisURL", description = "Redis URL", required = false)
    public String redisURL = "localhost";

    @Parameter(names = "--redisPort", description = "Redis port", required = false)
    public int redisPort = 6379;

    private Jedis jedis;

    public boolean useCache() {
        return useCache;
    }

    public synchronized Jedis getJedis() {
        if (jedis == null) {
            jedis = new Jedis(redisURL, redisPort);
        }
        return jedis;
    }

    public ACachingSolverDecorator createCachingSolverDecorator(ISolver solver, String graphHash) {
        return new RedisCachingSolverDecorator(solver, this, graphHash);
    }

}
