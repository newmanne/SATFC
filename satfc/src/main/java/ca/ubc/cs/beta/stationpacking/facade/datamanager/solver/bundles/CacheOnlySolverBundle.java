package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.CacherProxy;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.IComponentGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.AssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ConnectedComponentGroupingDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.UnderconstrainedStationRemoverSolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.SubsetCacheUNSATDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.SupersetCacheSATDecorator;

/**
 * Created by newmanne on 27/04/15.
 */
public class CacheOnlySolverBundle extends ASolverBundle {

    private ISolver cacheOnlySolver;

    public CacheOnlySolverBundle(IStationManager aStationManager, IConstraintManager aConstraintManager, String serverURL) {
        super(aStationManager, aConstraintManager);

        ICacher.CacheCoordinate cacheCoordinate = new ICacher.CacheCoordinate(aStationManager.getHashCode(), aConstraintManager.getHashCode());
        ContainmentCacheProxy containmentCache = new ContainmentCacheProxy(serverURL, cacheCoordinate);

        cacheOnlySolver = new CNFSolverBundle.VoidSolver();
        cacheOnlySolver = new SupersetCacheSATDecorator(cacheOnlySolver, containmentCache, cacheCoordinate);
        IComponentGrouper aGrouper = new ConstraintGrouper();
        cacheOnlySolver = new ConnectedComponentGroupingDecorator(cacheOnlySolver, aGrouper, getConstraintManager());
        cacheOnlySolver = new UnderconstrainedStationRemoverSolverDecorator(cacheOnlySolver, aConstraintManager);
        cacheOnlySolver = new SubsetCacheUNSATDecorator(cacheOnlySolver, containmentCache);
        cacheOnlySolver = new SupersetCacheSATDecorator(cacheOnlySolver, containmentCache, cacheCoordinate);
        cacheOnlySolver = new AssignmentVerifierDecorator(cacheOnlySolver, getConstraintManager());
    }

    @Override
    public ISolver getSolver(StationPackingInstance aInstance) {
        return cacheOnlySolver;
    }

    @Override
    public void close() throws Exception {
        cacheOnlySolver.notifyShutdown();
    }
}
