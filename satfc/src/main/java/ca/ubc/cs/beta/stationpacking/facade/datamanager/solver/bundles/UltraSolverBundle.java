package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.cache.CacherProxy;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.ClaspLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.UBCSATLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3ISolverFactory;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3LibraryGenerator;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.UBCSATISolverFactory;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.UBCSATLibraryGenerator;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.ConstraintGraphNeighborhoodPresolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.StationSubsetSATCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.StationSubsetUNSATCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.AddNeighbourLayerStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.IterativeDeepeningConfigurationStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.IComponentGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ISolverFactory;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ParallelNoWaitSolverComposite;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.AssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ConnectedComponentGroupingDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.UnderconstrainedStationRemoverSolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.CacheResultDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.SubsetCacheUNSATDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.SupersetCacheSATDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency.ArcConsistencyEnforcerDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.underconstrained.HeuristicUnderconstrainedStationFinder;

/**
 * Created by newmanne on 10/09/15.
 */
@Slf4j
public class UltraSolverBundle extends ASolverBundle {

    ISolver solver;

    /**
     * Create an abstract solver bundle with the given data management objects.
     *
     * @param aStationManager    - manages stations.
     * @param aConstraintManager - manages constraints.
     */
    public UltraSolverBundle(
            String aClaspLibraryPath,
            String aUBCSATLibraryPath,
            IStationManager aStationManager,
            IConstraintManager aConstraintManager,
            final String serverURL,
            int numCores,
            final boolean cacheResults

    ) {
        super(aStationManager, aConstraintManager);
        boolean useCache = serverURL != null;
        final SATCompressor aCompressor = new SATCompressor(this.getConstraintManager());
        final Clasp3ISolverFactory clasp3ISolverFactory = new Clasp3ISolverFactory(new Clasp3LibraryGenerator(aClaspLibraryPath), aCompressor, getConstraintManager());
        final UBCSATISolverFactory ubcsatiSolverFactory = new UBCSATISolverFactory(new UBCSATLibraryGenerator(aUBCSATLibraryPath), aCompressor, getConstraintManager());

        /**
         * Decorate solvers - remember that the decorator that you put first is applied last
         */
        final CacheCoordinate cacheCoordinate = new CacheCoordinate(aStationManager.getDomainHash(), aConstraintManager.getConstraintHash());

        final List<ISolverFactory> parallelUHFSolvers = new ArrayList<>();

        // BEGIN UHF
        // NOTE: The sorted order of this list matters if there are more solver paths than there are cores (i.e. first in list will go first)

        // Presolvers: these guys will expand range and use up all available time
        log.debug("Adding neighborhood presolvers.");
        parallelUHFSolvers.add(s -> new ConstraintGraphNeighborhoodPresolver(s,
                new StationSubsetSATCertifier(clasp3ISolverFactory.create(ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1)),
                new IterativeDeepeningConfigurationStrategy(new AddNeighbourLayerStrategy(), 10.0),
                getConstraintManager()));

        parallelUHFSolvers.add(s -> new ConstraintGraphNeighborhoodPresolver(s,
                new StationSubsetSATCertifier(ubcsatiSolverFactory.create(UBCSATLibSATSolverParameters.DEFAULT_DCCA)),
                new IterativeDeepeningConfigurationStrategy(new AddNeighbourLayerStrategy(), 10.0),
                getConstraintManager()));

        // Hit the cache at the instance level - we don't really count this one towards our numCores limit, because it will be I/O bound
        if (useCache) {
            parallelUHFSolvers.add(s -> {
                final ContainmentCacheProxy containmentCacheProxy = new ContainmentCacheProxy(serverURL, cacheCoordinate);
                ISolver UHFSolver = new SubsetCacheUNSATDecorator(s, containmentCacheProxy);// note that there is no need to check cache for UNSAT again, the first one would have caught it
                return new SupersetCacheSATDecorator(UHFSolver, containmentCacheProxy, cacheCoordinate);
            });
        }

        // Straight to clasp
        log.debug("Initializing base configured clasp solvers.");
        parallelUHFSolvers.add(s -> clasp3ISolverFactory.create(ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1));
        parallelUHFSolvers.add(s -> ubcsatiSolverFactory.create(UBCSATLibSATSolverParameters.DEFAULT_DCCA));


        parallelUHFSolvers.add(s -> {
            ISolver solver = clasp3ISolverFactory.create(ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1, 1);
            solver = new ConstraintGraphNeighborhoodPresolver(s,
                    new StationSubsetUNSATCertifier(clasp3ISolverFactory.create(ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1)),
                    new IterativeDeepeningConfigurationStrategy(new AddNeighbourLayerStrategy(), 10.0),
                    getConstraintManager());
            return solver;
        });

        // Decompose the problem and then hit the cache and then clasp
        final IComponentGrouper aGrouper = new ConstraintGrouper();
        parallelUHFSolvers.add(s -> {
            ISolver UHFSolver = clasp3ISolverFactory.create(ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h2);
            if (useCache) {
                final ContainmentCacheProxy containmentCacheProxy = new ContainmentCacheProxy(serverURL, cacheCoordinate);
                UHFSolver = new SupersetCacheSATDecorator(UHFSolver, containmentCacheProxy, cacheCoordinate); // note that there is no need to check cache for UNSAT again, the first one would have caught it
                if (cacheResults) {
                    UHFSolver = new AssignmentVerifierDecorator(UHFSolver, getConstraintManager()); // let's be careful and verify the assignment before we cache it
                    UHFSolver = new CacheResultDecorator(UHFSolver, new CacherProxy(serverURL, cacheCoordinate), cacheCoordinate);
                }
            }
            log.debug("Decorate solver to split the graph into connected components and then merge the results");
            UHFSolver = new ConnectedComponentGroupingDecorator(UHFSolver, aGrouper, getConstraintManager());
            log.debug("Decorate solver to first remove underconstrained stations.");
            UHFSolver = new UnderconstrainedStationRemoverSolverDecorator(UHFSolver, getConstraintManager(), new HeuristicUnderconstrainedStationFinder(getConstraintManager(), true), true);
            UHFSolver = new ArcConsistencyEnforcerDecorator(UHFSolver, getConstraintManager());
            return UHFSolver;
        });

        parallelUHFSolvers.add(s -> {
            ISolver UHFSolver = ubcsatiSolverFactory.create(UBCSATLibSATSolverParameters.DEFAULT_DCCA);
            if (useCache) {
                final ContainmentCacheProxy containmentCacheProxy = new ContainmentCacheProxy(serverURL, cacheCoordinate);
                UHFSolver = new SupersetCacheSATDecorator(UHFSolver, containmentCacheProxy, cacheCoordinate); // note that there is no need to check cache for UNSAT again, the first one would have caught it
                if (cacheResults) {
                    UHFSolver = new AssignmentVerifierDecorator(UHFSolver, getConstraintManager()); // let's be careful and verify the assignment before we cache it
                    UHFSolver = new CacheResultDecorator(UHFSolver, new CacherProxy(serverURL, cacheCoordinate), cacheCoordinate);
                }
            }
            log.debug("Decorate solver to split the graph into connected components and then merge the results");
            UHFSolver = new ConnectedComponentGroupingDecorator(UHFSolver, aGrouper, getConstraintManager());
            log.debug("Decorate solver to first remove underconstrained stations.");
            UHFSolver = new UnderconstrainedStationRemoverSolverDecorator(UHFSolver, getConstraintManager(), new HeuristicUnderconstrainedStationFinder(getConstraintManager(), true), true);
            UHFSolver = new ArcConsistencyEnforcerDecorator(UHFSolver, getConstraintManager());
            return UHFSolver;
        });


        // Init the parallel solvers
        solver = new ParallelNoWaitSolverComposite(numCores + 1, parallelUHFSolvers);
        // END UHF

        //Verify results.
        /*
         * NOTE: this is a MANDATORY decorator, and any decorator placed below this must not alter the answer or the assignment returned.
         */
        solver = new AssignmentVerifierDecorator(solver, getConstraintManager());

        // Cache entire instance. Placed below assignment verifier because we wouldn't want to cache something incorrect
        if (useCache && cacheResults) {
            solver = new CacheResultDecorator(solver, new CacherProxy(serverURL, cacheCoordinate), cacheCoordinate);
        }


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
