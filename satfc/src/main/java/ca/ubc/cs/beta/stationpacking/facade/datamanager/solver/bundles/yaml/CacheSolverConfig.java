package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml;

import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeParameter;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.YAMLBundle;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy;

/**
* Created by newmanne on 27/10/15.
*/
public abstract class CacheSolverConfig implements ISolverConfig {

    @Override
    public boolean shouldSkip(YAMLBundle.SATFCContext context) {
        return context.getParameter().getServerURL() == null;
    }

    protected ContainmentCacheProxy createContainmentCacheProxy(YAMLBundle.SATFCContext context) {
        final SATFCFacadeParameter parameter = context.getParameter();
        return new ContainmentCacheProxy(parameter.getServerURL(), context.getManagerBundle().getCacheCoordinate(), parameter.getNumServerAttempts(), parameter.isNoErrorOnServerUnavailable(), context.getPollingService(), context.getHttpClient());
    }

}
