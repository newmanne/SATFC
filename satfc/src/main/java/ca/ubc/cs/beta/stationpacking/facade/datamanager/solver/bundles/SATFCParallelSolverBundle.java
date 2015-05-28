/**
 * Copyright 2015, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 *
 * This file is part of SATFC.
 *
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import ca.ubc.cs.beta.stationpacking.cache.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ISolverFactory;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ParallelNoWaitSolverComposite;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ParallelSolverComposite;
import ca.ubc.cs.beta.stationpacking.solvers.composites.SequentialSolversComposite;
import ca.ubc.cs.beta.stationpacking.solvers.termination.cputime.CPUTimeTerminationCriterionFactory;
import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.CacherProxy;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.ClaspLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.ConstraintGraphNeighborhoodPresolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.StationSubsetSATCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.IComponentGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.AssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ConnectedComponentGroupingDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ResultSaverSolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.UnderconstrainedStationRemoverSolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.CacheResultDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.ContainmentCacheProxy;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.SubsetCacheUNSATDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.cache.SupersetCacheSATDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.sat.CompressedSATBasedSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.Clasp3SATSolver;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;

/**
 * Created by newmanne on 14/05/15.
 * SATFC Solver bundle that performs executions in parallel
 */
@Slf4j
public class SATFCParallelSolverBundle extends ASolverBundle {

    private final ISolver fUHFSolver;
    private final ISolver fVHFSolver;
    private final ClaspLibraryGenerator claspLibraryGenerator;

    /**
     * Create a SATFC solver bundle.
     *
     * @param aClaspLibraryPath  - library for the clasp to use.
     * @param aStationManager    - station manager.
     * @param aConstraintManager - constraint manager.
     * @param aResultFile        - file to which results should be written (optional).
     */
    public SATFCParallelSolverBundle(
            String aClaspLibraryPath,
            IStationManager aStationManager,
            IConstraintManager aConstraintManager,
            String aResultFile,
            final boolean presolve,
            final boolean decompose,
            final boolean underconstrained,
            final String serverURL
    ) {
        super(aStationManager, aConstraintManager);
        log.info("Initializing solver with the following solver options: presolve {}, decompose {}, underconstrained {}, serverURL {}", presolve, decompose, underconstrained, serverURL);
        boolean useCache = serverURL != null;
        claspLibraryGenerator = new ClaspLibraryGenerator(aClaspLibraryPath);
        final SATCompressor aCompressor = new SATCompressor(this.getConstraintManager());

        log.debug("SATFC solver bundle.");

        /**
         * Decorate solvers - remember that the decorator that you put first is applied last
         */
        final CacheCoordinate cacheCoordinate = new CacheCoordinate(aStationManager.getHashCode(), aConstraintManager.getHashCode());

        final List<ISolverFactory> parallelUHFSolvers = new ArrayList<>();
        final List<ISolverFactory> parallelVHFSolvers = new ArrayList<>();

        // NOTE: The sorted order of this list matters if there are more solver paths than there are cores (i.e. first in list will go first)
        // Hit the cache at the instance level
        if (useCache) {
            parallelUHFSolvers.add(() -> {
                final ContainmentCacheProxy containmentCacheProxy = new ContainmentCacheProxy(serverURL, cacheCoordinate);
                final SubsetCacheUNSATDecorator UHFsubsetCacheUNSATDecorator = new SubsetCacheUNSATDecorator(new VoidSolver(), containmentCacheProxy);// note that there is no need to check cache for UNSAT again, the first one would have caught it
                return new SupersetCacheSATDecorator(UHFsubsetCacheUNSATDecorator, containmentCacheProxy, cacheCoordinate);
            });
        }

        // Presolvers: these guys will expand range and use up all available time
        if (presolve) {
            log.debug("Adding neighborhood presolvers.");
            final double SATcertifiercutoff = 5.0;
            parallelUHFSolvers.add(() -> new ConstraintGraphNeighborhoodPresolver(aConstraintManager,
                    Arrays.asList(new StationSubsetSATCertifier(getClaspSolver(aCompressor, ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1), new CPUTimeTerminationCriterionFactory(SATcertifiercutoff))), 1));
            parallelVHFSolvers.add(() -> new ConstraintGraphNeighborhoodPresolver(aConstraintManager,
                    Arrays.asList(new StationSubsetSATCertifier(getClaspSolver(aCompressor, ClaspLibSATSolverParameters.HVHF_CONFIG_09_13_MODIFIED), new CPUTimeTerminationCriterionFactory(SATcertifiercutoff))), 1));
        }

        // Straight to clasp
        log.debug("Initializing base configured clasp solvers.");
        parallelUHFSolvers.add(() -> getClaspSolver(aCompressor, ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1));
        parallelVHFSolvers.add(() -> getClaspSolver(aCompressor, ClaspLibSATSolverParameters.HVHF_CONFIG_09_13_MODIFIED));

        // Decompose the problem and then hit the cache and then clasp
        if (decompose || underconstrained) {
            final IComponentGrouper aGrouper = new ConstraintGrouper();
            parallelUHFSolvers.add(() -> {
                ISolver UHFSolver = getClaspSolver(aCompressor, ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h2);
                if (decompose) {
                    log.debug("Decorate solver to split the graph into connected components and then merge the results");
                    UHFSolver = new ConnectedComponentGroupingDecorator(UHFSolver, aGrouper, getConstraintManager());
                }
                if (underconstrained) {
                    //Remove unconstrained stations.
                    log.debug("Decorate solver to first remove underconstrained stations.");
                    UHFSolver = new UnderconstrainedStationRemoverSolverDecorator(UHFSolver, getConstraintManager());
                }
                if (useCache) {
                    final ContainmentCacheProxy containmentCacheProxy = new ContainmentCacheProxy(serverURL, cacheCoordinate);
                    UHFSolver = new SupersetCacheSATDecorator(UHFSolver, containmentCacheProxy, cacheCoordinate); // note that there is no need to check cache for UNSAT again, the first one would have caught it
                    UHFSolver = new AssignmentVerifierDecorator(UHFSolver, getConstraintManager()); // let's be careful and verify the assignment before we cache it
                    UHFSolver = new CacheResultDecorator(UHFSolver, new CacherProxy(serverURL, cacheCoordinate), cacheCoordinate);
                }
                return UHFSolver;
            });
            parallelVHFSolvers.add(() -> {
                ISolver VHFSolver = getClaspSolver(aCompressor, ClaspLibSATSolverParameters.HVHF_CONFIG_09_13_MODIFIED);
                if (decompose) {
                    // Split into components
                    VHFSolver = new ConnectedComponentGroupingDecorator(VHFSolver, aGrouper, getConstraintManager());
                }
                if (underconstrained) {
                    VHFSolver = new UnderconstrainedStationRemoverSolverDecorator(VHFSolver, getConstraintManager());
                }
                return VHFSolver;
            });
        }
        // END PATHS

        // Init the parallel solvers
        ISolver UHFsolver = new ParallelNoWaitSolverComposite(parallelUHFSolvers.size(), parallelUHFSolvers);
        ISolver VHFsolver = new ParallelNoWaitSolverComposite(parallelVHFSolvers.size(), parallelVHFSolvers);

        //Save results, if needed.
        if (aResultFile != null) {
            log.debug("Decorate solver to save results.");
            UHFsolver = new ResultSaverSolverDecorator(UHFsolver, aResultFile);
            VHFsolver = new ResultSaverSolverDecorator(VHFsolver, aResultFile);
        }

        //Verify results.
        /*
         * NOTE: this is a MANDATORY decorator, and any decorator placed below this must not alter the answer or the assignment returned.
         */
        UHFsolver = new AssignmentVerifierDecorator(UHFsolver, getConstraintManager());
        VHFsolver = new AssignmentVerifierDecorator(VHFsolver, getConstraintManager());

        // Cache entire instance. Placed below assignment verifier because we wouldn't want to cache something incorrect
        if (useCache) {
            UHFsolver = new CacheResultDecorator(UHFsolver, new CacherProxy(serverURL, cacheCoordinate), cacheCoordinate);
        }

        fUHFSolver = UHFsolver;
        fVHFSolver = VHFsolver;
    }

    private ISolver getClaspSolver(SATCompressor aSATCompressor, String aConfig) {
        final AbstractCompressedSATSolver claspSATsolver = new Clasp3SATSolver(claspLibraryGenerator.createClaspLibrary(), aConfig);
        return new CompressedSATBasedSolver(claspSATsolver, aSATCompressor, this.getConstraintManager());
    }

    @Override
    public ISolver getSolver(StationPackingInstance aInstance) {
        //Return the right solver based on the channels in the instance.
        if (StationPackingUtils.HVHF_CHANNELS.containsAll(aInstance.getAllChannels()) || StationPackingUtils.LVHF_CHANNELS.containsAll(aInstance.getAllChannels())) {
            log.debug("Returning clasp configured for VHF");
            return fVHFSolver;
        } else {
            log.debug("Returning clasp configured for UHF");
            return fUHFSolver;
        }
    }

    @Override
    public void close() {
        fUHFSolver.notifyShutdown();
        fVHFSolver.notifyShutdown();
    }

}