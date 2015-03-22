package ca.ubc.cs.beta.stationpacking.execution.parameters;

import lombok.Getter;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;

import com.beust.jcommander.Parameter;

/**
 * Created by newmanne on 04/12/14.
 */
@UsageTextField(title="SATFC Caching Parameters",description="Parameters for the SATFC problem cache.")
public class SATFCCachingParameters extends AbstractOptions {

    @Parameter(names = "--useCache", description = "Should the cache be used", required = false, arity = 0)
    public boolean useCache = false;

    @Parameter(names = "--serverURL", description = "base URL for the SATFC server", required = false)
    @Getter
    public String serverURL = "http://localhost:8080/satfcserver";

}
