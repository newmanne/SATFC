package ca.ubc.cs.beta.stationpacking.execution.parameters;

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
    public boolean useCache = true;

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
            // TODO: log some stuff
            jedis = new Jedis(redisURL, redisPort);
        }
        return jedis;
    }

}
