package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.cache.CacherProxy;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.ClaspLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3ISolverFactory;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.ClaspLibraryGenerator;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.PythonInterpreterFactory;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.IComponentGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.*;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.CacheResultDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.SubsetCacheUNSATDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.SupersetCacheSATDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency.ArcConsistencyEnforcerDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency.ChannelKillerDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.underconstrained.HeuristicUnderconstrainedStationFinder;

/**
 * Created by newmanne on 20/08/15.
 */
@Slf4j
public class LongCutoffSolverBundle extends ASolverBundle {

    private ISolver solver;

    public LongCutoffSolverBundle(
            String aClaspLibraryPath,
            ManagerBundle dataBundle,
            String serverURL
    ) {
        super(dataBundle);

        final PythonInterpreterFactory python = new PythonInterpreterFactory(getInterferenceFolder(), getCompact());
        IStationManager aStationManager = dataBundle.getStationManager();
        IConstraintManager aConstraintManager = dataBundle.getConstraintManager();
        SATCompressor aCompressor = new SATCompressor(this.getConstraintManager());
        final Clasp3ISolverFactory clasp3ISolverFactory = new Clasp3ISolverFactory(new ClaspLibraryGenerator(aClaspLibraryPath), aCompressor, getConstraintManager());
        CacheCoordinate cacheCoordinate = new CacheCoordinate(aStationManager.getDomainHash(), aConstraintManager.getConstraintHash());
        ICacher cacher = new CacherProxy(serverURL, cacheCoordinate);
        ContainmentCacheProxy containmentCache = new ContainmentCacheProxy(serverURL, cacheCoordinate);
        IComponentGrouper aGrouper = new ConstraintGrouper();

        log.debug("Initializing base configured clasp solvers.");

        solver = clasp3ISolverFactory.create(ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h2);
        solver = new SupersetCacheSATDecorator(solver, containmentCache, cacheCoordinate);
        solver = new PythonAssignmentVerifierDecorator(solver, python);
        solver = new AssignmentVerifierDecorator(solver, getConstraintManager(), getStationManager());
        solver = new CacheResultDecorator(solver, cacher, cacheCoordinate);
        solver = new ConnectedComponentGroupingDecorator(solver, aGrouper, getConstraintManager());
        solver = new UnderconstrainedStationRemoverSolverDecorator(solver, getConstraintManager(), new HeuristicUnderconstrainedStationFinder(getConstraintManager(), true), true);
        solver = new ChannelKillerDecorator(solver, clasp3ISolverFactory.create(ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1), getConstraintManager(), 0.2);
        solver = new ArcConsistencyEnforcerDecorator(solver, getConstraintManager());
        solver = new SubsetCacheUNSATDecorator(solver, containmentCache);
        solver = new SupersetCacheSATDecorator(solver, containmentCache, cacheCoordinate);
        solver = new PythonAssignmentVerifierDecorator(solver, python);
        solver = new AssignmentVerifierDecorator(solver, getConstraintManager(), getStationManager());
        solver = new CacheResultDecorator(solver, cacher, cacheCoordinate);
    }

    @Override
    public ISolver getSolver(StationPackingInstance aInstance) {
        return solver;
    }

    @Override
    public void close() throws Exception {
        solver.notifyShutdown();
    }
}
