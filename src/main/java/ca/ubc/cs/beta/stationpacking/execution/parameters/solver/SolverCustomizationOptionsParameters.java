package ca.ubc.cs.beta.stationpacking.execution.parameters.solver;

import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.stationpacking.database.CachingDecoratorFactory;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCCachingParameters;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeParameter;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.base.Preconditions;

import java.util.Objects;

/**
 * Created by newmanne on 13/01/15.
 */
@UsageTextField(title="Solver customization options",description="Parameters describing which optimizations to apply")
public class SolverCustomizationOptionsParameters extends AbstractOptions {


        @Parameter(names = "--presolve", description = "pre-solving", arity = 1)
        private boolean presolve = true;
        @Parameter(names = "--underconstrained", description = "underconstrained station optimizations", arity = 1)
        private boolean underconstrained = true;
        @Parameter(names = "--decomposition", description = "connected component decomposition", arity = 1)
        private boolean decomposition = true;
        @ParametersDelegate
        private SATFCCachingParameters cachingParams = new SATFCCachingParameters();

        public SATFCFacadeParameter.SolverCustomizationOptions getOptions() {
            SATFCFacadeParameter.SolverCustomizationOptions options = new SATFCFacadeParameter.SolverCustomizationOptions();
            options.setPresolve(presolve);
            options.setUnderconstrained(underconstrained);
            options.setDecompose(decomposition);
            if (cachingParams.useCache) 
            {
                options.setCache(true);
            	options.setCacheGraphKey(cachingParams.cacheGraphKey);
                final CachingDecoratorFactory cachingDecoratorFactory = cachingParams.getCachingDecoratorFactory();
                options.setCachingDecoratorFactory(cachingDecoratorFactory);
            }
            return options;
        }

}
