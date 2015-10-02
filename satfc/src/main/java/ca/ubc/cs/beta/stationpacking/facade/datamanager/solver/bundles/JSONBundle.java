package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import ca.ubc.cs.beta.stationpacking.cache.CacherProxy;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.smac.SATFCHydraParams.SolverType;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3LibraryGenerator;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.UBCSATLibraryGenerator;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
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
import ca.ubc.cs.beta.stationpacking.solvers.sat.CompressedSATBasedSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.Clasp3SATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.ubcsat.UBCSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.underconstrained.HeuristicUnderconstrainedStationFinder;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Builder;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by newmanne on 01/10/15.
 * Builds a bundle based on a JSON description
 */
@Slf4j
public class JSONBundle extends AVHFUHFSolverBundle {

    @Getter
    private final ISolver UHFSolver;
    @Getter
    private final ISolver VHFSolver;

    public JSONBundle(
            IStationManager aStationManager,
            IConstraintManager aConstraintManager,
            String configFile,
            String serverURL,
            String claspLibraryPath,
            String ubcsatLibraryPath
    ) {
        super(aStationManager, aConstraintManager);
        final String configJSONString;
        try {
            configJSONString = Files.toString(new File(configFile), Charsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load in config file", e);
        }
        Map<String, List<SolverConfig>> bandToConfig = null;
        try {
            bandToConfig = JSONUtils.getMapper().readValue(configJSONString, new TypeReference<Map<String, List<SolverConfig>>>() {
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        final List<SolverConfig> uhf = bandToConfig.get("UHF");
        final List<SolverConfig> vhf = bandToConfig.get("VHF");

        final SATFCContext context = SATFCContext
                .builder()
                .managerBundle(new ManagerBundle(aStationManager, aConstraintManager))
                .clasp3LibraryGenerator(new Clasp3LibraryGenerator(claspLibraryPath))
                .ubcsatLibraryGenerator(new UBCSATLibraryGenerator(ubcsatLibraryPath))
                .serverURL(serverURL)
                .build();

        UHFSolver = concat(uhf, context);
        VHFSolver = concat(vhf, context);
    }

    private static ISolver concat(List<SolverConfig> configs, SATFCContext context) {
        ISolver solver = new VoidSolver();
        for (SolverConfig config : configs) {
            solver = config.createSolver(context, solver);
        }
        return solver;
    }

    @Data
    public static class JSONBundleConfig {
        Map<String , List<SolverConfig>> bandToConfig;

    }

    public interface SolverConfig {
        SolverType getType();
        ISolver createSolver(SATFCContext context, ISolver solverToDecorate);
        default ISolver createSolver(SATFCContext context) {
            return createSolver(context, new VoidSolver());
        }
        default boolean isTerminal() { return false; }
    }

    @Builder
    @Data
    public static class SATFCContext {
        private final String serverURL;
        private final Clasp3LibraryGenerator clasp3LibraryGenerator;
        private final UBCSATLibraryGenerator ubcsatLibraryGenerator;
        private final ManagerBundle managerBundle;
    }

    @Data
    public static class ClaspConfig implements SolverConfig {

        @Override
        public SolverType getType() {
            return SolverType.CLASP;
        }

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            final IConstraintManager constraintManager = context.getManagerBundle().getConstraintManager();
            final Clasp3LibraryGenerator clasp3LibraryGenerator = context.getClasp3LibraryGenerator();
            final AbstractCompressedSATSolver claspSATsolver = new Clasp3SATSolver(clasp3LibraryGenerator.createLibrary(), config);
            return new CompressedSATBasedSolver(claspSATsolver, new SATCompressor(constraintManager));
        }

        @Override
        public boolean isTerminal() {
            return true;
        }

        private String config;
        // TODO: use
//        private EncodingType encodingType;
    }

    @Data
    public static class UBCSATConfig implements SolverConfig {

        @Override
        public SolverType getType() {
            return SolverType.UBCSAT;
        }

        @Override
        public boolean isTerminal() {
            return true;
        }

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            final IConstraintManager constraintManager = context.getManagerBundle().getConstraintManager();
            final UBCSATLibraryGenerator ubcsatLibraryGenerator = context.getUbcsatLibraryGenerator();
            final AbstractCompressedSATSolver ubcsatSolver = new UBCSATSolver(ubcsatLibraryGenerator.createLibrary(), config);
            return new CompressedSATBasedSolver(ubcsatSolver, new SATCompressor(constraintManager));
        }

        private String config;
        // TODO: use
//        private EncodingType encodingType;
    }

    @Data
    public static class AssignmentVerifierConfig implements SolverConfig {

        @Override
        public SolverType getType() {
            return SolverType.VERIFIER;
        }

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new AssignmentVerifierDecorator(solverToDecorate, context.getManagerBundle().getConstraintManager());
        }

    }

    @Data
    public static class ConnectedComponentsConfig implements SolverConfig {

        @Override
        public SolverType getType() {
            return SolverType.CONNECTED_COMPONENTS;
        }

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new ConnectedComponentGroupingDecorator(solverToDecorate, new ConstraintGrouper(), context.getManagerBundle().getConstraintManager());
        }
    }

    @Data
    public static class ArcConsistencyConfig implements SolverConfig {

        @Override
        public SolverType getType() {
            return SolverType.ARC_CONSISTENCY;
        }

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new ArcConsistencyEnforcerDecorator(solverToDecorate, context.getManagerBundle().getConstraintManager());
        }
    }

    @Data
    public static class CacheConfig implements SolverConfig {

        @Override
        public SolverType getType() {
            return SolverType.CACHE;
        }

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new CacheResultDecorator(solverToDecorate, new CacherProxy(context.getServerURL()), context.getManagerBundle().getCacheCoordinate());
        }
    }

    @Data
    public static class SATCacheConfig implements SolverConfig {


        @Override
        public SolverType getType() {
            return SolverType.SAT_CACHE;
        }

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new SupersetCacheSATDecorator(solverToDecorate, new ContainmentCacheProxy(context.getServerURL(), context.getManagerBundle().getCacheCoordinate()));
        }
    }

    @Data
    public static class UNSATCacheConfig implements SolverConfig {


        @Override
        public SolverType getType() {
            return SolverType.UNSAT_CACHE;
        }

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new SubsetCacheUNSATDecorator(solverToDecorate, new ContainmentCacheProxy(context.getServerURL(), context.getManagerBundle().getCacheCoordinate()));
        }
    }

    @Data
    public static class UnderconstrainedConfig implements SolverConfig {

        @Override
        public SolverType getType() {
            return SolverType.UNDERCONSTRAINED;
        }

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new UnderconstrainedStationRemoverSolverDecorator(solverToDecorate, context.getManagerBundle().getConstraintManager(), new HeuristicUnderconstrainedStationFinder(context.getManagerBundle().getConstraintManager(), expensive), isRecursive);
        }

        private boolean expensive;
        private boolean isRecursive;
    }
//
//    @Data
//    public static class SATPresolver implements SolverConfig {
//
//        @Override
//        public SolverType getType() {
//            return SolverType.SAT_PRESOLVER;
//        }
//
//        @Override
//        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
//            new ConstraintGraphNeighborhoodPresolver(solverToDecorate,
//                    new StationSubsetSATCertifier(solverConfig.createSolver(context)),
//                    new IterativeDeepeningConfigurationStrategy())
//        }
//
//        private SolverConfig solverConfig;
//        private
//
//    }
//
//    @Data
//    public static class UNSATPresolver implements SolverConfig {
//
//        @Override
//        public SolverType getType() {
//            return SolverType.UNSAT_PRESOLVER;
//        }
//
//        private SolverConfig solverConfig;
//
//    }

    @Data
    public static class ParallelConfig implements SolverConfig {


        @Override
        public SolverType getType() {
            return SolverType.PARALLEL;
        }

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            List<ISolverFactory> solverFactories = new ArrayList<>();
            for (List<SolverConfig> configPath : channels) {
                solverFactories.add(aSolver -> {
                    return concat(configPath, context);
                });
            }
            return new ParallelNoWaitSolverComposite(solverFactories.size(), solverFactories);
        }

        @Override
        public boolean isTerminal() {
            return true;
        }

        private List<List<SolverConfig>> channels;

    }

    public enum EncodingType {
        DIRECT, MULTIVALUED
    }

}
