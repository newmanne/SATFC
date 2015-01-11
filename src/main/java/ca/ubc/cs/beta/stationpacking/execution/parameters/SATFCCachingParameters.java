package ca.ubc.cs.beta.stationpacking.execution.parameters;

import lombok.Getter;
import ca.ubc.cs.beta.stationpacking.database.CachingDecoratorFactory;
import ca.ubc.cs.beta.stationpacking.database.RedisCachingDecoratorFactory;

import com.beust.jcommander.Parameter;
import com.google.common.net.HostAndPort;

/**
 * Created by newmanne on 04/12/14.
 */
public class SATFCCachingParameters {

    @Parameter(names = "--useCache", description = "Should the cache be used", required = false)
    @Getter
    public boolean useCache = false;

    @Parameter(names = "--redisURL", description = "Redis URL", required = false)
    public String redisURL = "localhost";

    @Parameter(names = "--redisPort", description = "Redis port", required = false)
    public int redisPort = 6379;
    
    @Parameter(names = "--cacheInterferenceName", description = "Interference name (for caching)", required = false)
    public String interference;
    
    public CachingDecoratorFactory getCachingDecoratorFactory() {
    	return useCache ? new RedisCachingDecoratorFactory(redisURL, redisPort) : null;
    }

}
