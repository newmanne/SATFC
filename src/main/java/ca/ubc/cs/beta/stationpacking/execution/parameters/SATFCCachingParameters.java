package ca.ubc.cs.beta.stationpacking.execution.parameters;

import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import lombok.Getter;
import ca.ubc.cs.beta.stationpacking.database.CachingDecoratorFactory;
import ca.ubc.cs.beta.stationpacking.database.RedisCachingDecoratorFactory;

import com.beust.jcommander.Parameter;
import com.google.common.base.Preconditions;
import com.google.common.net.HostAndPort;

/**
 * Created by newmanne on 04/12/14.
 */
@UsageTextField(title="SATFC Caching Parameters",description="Parameters for the SATFC problem cache.")
public class SATFCCachingParameters extends AbstractOptions {

    @Parameter(names = "--useCache", description = "Should the cache be used", required = false, arity = 0)
    public boolean useCache = false;

    @Parameter(names = "--redisURL", description = "Redis URL", required = false)
    public String redisURL = "localhost";

    @Parameter(names = "--redisPort", description = "Redis port", required = false)
    public int redisPort = 6379;
    
    @Parameter(names = "--cacheGraphKey", description = "Interference name (for caching)", required = false)
    public String cacheGraphKey;
    
    public CachingDecoratorFactory getCachingDecoratorFactory() {
    	Preconditions.checkState(useCache);
        Preconditions.checkNotNull(cacheGraphKey);
        Preconditions.checkNotNull(redisPort);
        Preconditions.checkNotNull(redisURL);
    	return new RedisCachingDecoratorFactory(redisURL, redisPort);
    }

}
