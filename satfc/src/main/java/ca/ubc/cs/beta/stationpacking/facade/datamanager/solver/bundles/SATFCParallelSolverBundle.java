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

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.CacherProxy;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat.ClaspLibSATSolverParameters;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.ConstraintGraphNeighborhoodPresolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.StationSubsetSATCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.IComponentGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ParallelSolverComposite;
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
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.Clasp3Library;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.Clasp3SATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.termination.cputime.CPUTimeTerminationCriterionFactory;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.io.Files;
import com.sun.jna.Native;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by newmanne on 14/05/15.
 * SATFC Solver bundle that performs executions in parallel
 */
@Slf4j
public class SATFCParallelSolverBundle extends ASolverBundle {

    private final ISolver fUHFSolver;
    private final ISolver fVHFSolver;

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
            boolean presolve,
            boolean decompose,
            boolean underconstrained,
            String serverURL
    ) {
        super(aStationManager, aConstraintManager);
        log.info("Initializing solver with the following solver options: presolve {}, decompose {}, underconstrained {}, serverURL {}", presolve, decompose, underconstrained, serverURL);
        boolean useCache = serverURL != null;
        final ClaspLibraryGenerator claspLibraryGenerator = new ClaspLibraryGenerator(aClaspLibraryPath);
        final SATCompressor aCompressor = new SATCompressor(this.getConstraintManager());

        log.debug("SATFC solver bundle.");

        /**
         * Decorate solvers - remember that the decorator that you put first is applied last
         */
        ContainmentCacheProxy containmentCache = null;
        ICacher cacher = null;
        ICacher.CacheCoordinate cacheCoordinate = null;
        if (useCache) {
            cacheCoordinate = new ICacher.CacheCoordinate(aStationManager.getHashCode(), aConstraintManager.getHashCode());
            cacher = new CacherProxy(serverURL, cacheCoordinate);
            containmentCache = new ContainmentCacheProxy(serverURL, cacheCoordinate);
        }

        List<ISolver> parallelUHFSolvers = new ArrayList<>();
        List<ISolver> parallelVHFSolvers = new ArrayList<>();

        // NOTE: The sorted order of this list matters if there are more solver paths than there are cores (i.e. first in list will go first)
        // BEGIN PATHS
        // Path 1 - Hit the cache at the instance level
        if (useCache) {
            final SubsetCacheUNSATDecorator UHFsubsetCacheUNSATDecorator = new SubsetCacheUNSATDecorator(new VoidSolver(), containmentCache);// note that there is no need to check cache for UNSAT again, the first one would have caught it
            final SupersetCacheSATDecorator UHFsupersetCacheSATDecorator = new SupersetCacheSATDecorator(UHFsubsetCacheUNSATDecorator, containmentCache, cacheCoordinate);
            parallelUHFSolvers.add(UHFsupersetCacheSATDecorator);
        }

        // Path 2 - Decompose the problem and then hit the cache and then clasp
        if (decompose || underconstrained) {
            Clasp3Library path2Library = claspLibraryGenerator.createClaspLibrary();
            ISolver UHFSolver = getClaspSolver(aCompressor, ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h2, path2Library);
            ISolver VHFSolver = getClaspSolver(aCompressor, ClaspLibSATSolverParameters.HVHF_CONFIG_09_13_MODIFIED, path2Library);
            if (decompose) {
                // Split into components
                IComponentGrouper aGrouper = new ConstraintGrouper();
                log.debug("Decorate solver to split the graph into connected components and then merge the results");
                UHFSolver = new ConnectedComponentGroupingDecorator(UHFSolver, aGrouper, getConstraintManager());
                VHFSolver = new ConnectedComponentGroupingDecorator(VHFSolver, aGrouper, getConstraintManager());
            }
            if (underconstrained) {
                //Remove unconstrained stations.
                log.debug("Decorate solver to first remove underconstrained stations.");
                UHFSolver = new UnderconstrainedStationRemoverSolverDecorator(UHFSolver, getConstraintManager());
                VHFSolver = new UnderconstrainedStationRemoverSolverDecorator(VHFSolver, getConstraintManager());
            }
            if (useCache) {
                UHFSolver = new SupersetCacheSATDecorator(UHFSolver, containmentCache, cacheCoordinate); // note that there is no need to check cache for UNSAT again, the first one would have caught it
                UHFSolver = new AssignmentVerifierDecorator(UHFSolver, getConstraintManager()); // let's be careful and verify the assignment before we cache it
                UHFSolver = new CacheResultDecorator(UHFSolver, cacher, cacheCoordinate);
            }
            parallelUHFSolvers.add(UHFSolver);
            parallelVHFSolvers.add(VHFSolver);
        }

        // Path 3 - Presolver
        if (presolve) {
            final double SATcertifiercutoff = 5;
            log.debug("Adding neighborhood presolvers.");
            Clasp3Library path3Library = claspLibraryGenerator.createClaspLibrary();
            final ConstraintGraphNeighborhoodPresolver UHFconstraintGraphNeighborhoodPresolver = new ConstraintGraphNeighborhoodPresolver(aConstraintManager,
                    Arrays.asList(new StationSubsetSATCertifier(getClaspSolver(aCompressor, ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1, path3Library), new CPUTimeTerminationCriterionFactory(SATcertifiercutoff))));
            parallelUHFSolvers.add(UHFconstraintGraphNeighborhoodPresolver);

            final ConstraintGraphNeighborhoodPresolver VHFconstraintGraphNeighborhoodPresolver = new ConstraintGraphNeighborhoodPresolver(aConstraintManager,
                    Arrays.asList(new StationSubsetSATCertifier(getClaspSolver(aCompressor, ClaspLibSATSolverParameters.HVHF_CONFIG_09_13_MODIFIED, path3Library), new CPUTimeTerminationCriterionFactory(SATcertifiercutoff))));
            parallelVHFSolvers.add(VHFconstraintGraphNeighborhoodPresolver);
        }

        // Path 4 - Straight to clasp
        log.debug("Initializing base configured clasp solvers.");
        Clasp3Library path4Library = claspLibraryGenerator.createClaspLibrary();
        parallelUHFSolvers.add(getClaspSolver(aCompressor, ClaspLibSATSolverParameters.UHF_CONFIG_04_15_h1, path4Library));
        parallelVHFSolvers.add(getClaspSolver(aCompressor, ClaspLibSATSolverParameters.HVHF_CONFIG_09_13_MODIFIED, path4Library));
        // END PATHS

        // Init the parallel solvers
        ISolver UHFsolver = new ParallelSolverComposite(parallelUHFSolvers);
        ISolver VHFsolver = new ParallelSolverComposite(parallelVHFSolvers);

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
            UHFsolver = new CacheResultDecorator(UHFsolver, cacher, cacheCoordinate);
        }

        fUHFSolver = UHFsolver;
        fVHFSolver = VHFsolver;
    }

    private ISolver getClaspSolver(SATCompressor aSATCompressor, String aConfig, Clasp3Library library) {
        AbstractCompressedSATSolver claspSATsolver = new Clasp3SATSolver(library, aConfig);
        return new CompressedSATBasedSolver(claspSATsolver, aSATCompressor, this.getConstraintManager());
    }

    /**
     * This class returns a fresh, independent clasp library to each thread
     * We need to use this class because clasp is (probably?) not re-entrant
     * Because of the way JNA works, we need a new physical copy of the library each time we launch
     */
    public static class ClaspLibraryGenerator {
        private final String libraryPath;
        private int numClasps;

        public ClaspLibraryGenerator(String libraryPath) {
            this.libraryPath = libraryPath;
            numClasps = 0;
        }

        public Clasp3Library createClaspLibrary() {
            File origFile = new File(libraryPath);
            try {
                File copy = File.createTempFile(Files.getNameWithoutExtension(libraryPath) + "_" + ++numClasps, Files.getFileExtension(libraryPath));
                Clasp3Library libCopy = (Clasp3Library) Native.loadLibrary(copy.getPath(), Clasp3Library.class);
                Files.copy(origFile, copy);
                return libCopy;
            } catch (IOException e) {
                throw new RuntimeException("Couldn't create a copy of clasp!!!");
            }
        }
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
