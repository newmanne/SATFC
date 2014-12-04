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
    public double minTimeToCache;

    // smallest amount of time for which you will take a cached timeout at its word
    @Getter
    public double minTimeToTrustCache;

    @Parameter(names = "--useCache", description = "Should the cache be used", required = false)
    public boolean useCache;

    @Parameter(names = "--redisURL", description = "Redis URL", required = false)
    public String redisURL;

    @Parameter(names = "--redisPort", description = "Redis port", required = false)
    public int redisPort;

    private Jedis jedis;

    public boolean useCache() {
        return useCache;
    }

    public synchronized Jedis getJedis() {
        if (jedis == null) {
            jedis = new Jedis("localhost", 6379);
        }
        return jedis;
    }
}
