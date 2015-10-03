package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import ca.ubc.cs.beta.stationpacking.cache.CacherProxy;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.smac.SATFCHydraParams;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3LibraryGenerator;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.UBCSATLibraryGenerator;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.ConstraintGraphNeighborhoodPresolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.StationSubsetSATCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.*;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ISolverFactory;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ParallelNoWaitSolverComposite;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.AssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ConnectedComponentGroupingDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ResultSaverSolverDecorator;
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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Builder;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
            String ubcsatLibraryPath,
            String resultFile
    ) {
        super(aStationManager, aConstraintManager);

        log.info("Using the following variables to build the bundle: configFile={}, serverURL={}, clasp={}, ubcsat={}", configFile, serverURL, claspLibraryPath, ubcsatLibraryPath);

        final SATFCContext context = SATFCContext
                .builder()
                .managerBundle(new ManagerBundle(aStationManager, aConstraintManager))
                .clasp3LibraryGenerator(new Clasp3LibraryGenerator(claspLibraryPath))
                .ubcsatLibraryGenerator(new UBCSATLibraryGenerator(ubcsatLibraryPath))
                .serverURL(serverURL)
                .build();

        log.info("Reading configuration file {}", configFile);
        final String configJSONString;
        try {
            configJSONString = Files.toString(new File(configFile), Charsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load in config file", e);
        }

        log.info("Parsing configuration file {}", configFile);
        final JSONBundleConfig config;
        try {
            config = JSONUtils.toObjectWithException(configJSONString, JSONBundleConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't parse JSON from file " + configFile, e);
        }

        log.info("Configuration parsed! Building solvers...");
        final List<SolverConfig> uhf = config.getUHF();
        final List<SolverConfig> vhf = config.getVHF();
        Preconditions.checkState(!uhf.isEmpty(), "No solver provided for UHF in config file %s", configFile);
        Preconditions.checkState(!vhf.isEmpty(), "No solver provided for VHF in config file %s", configFile);

        UHFSolver = concat(uhf, context);
        VHFSolver = concat(vhf, context);
    }

    private static ISolver concat(List<SolverConfig> configs, SATFCContext context) {
        log.info("Starting with a void solver...");
        ISolver solver = new VoidSolver();
        for (SolverConfig config : configs) {
            if (!config.shouldSkip(context)) {
                solver = config.createSolver(context, solver);
                log.info("Decorating with {} using config of type {}", solver.getClass().getSimpleName(), config.getClass().getSimpleName());
            } else {
                log.info("Skipping decorator {}", config.getClass().getSimpleName());
            }
        }
        return solver;
    }

    @Data
    public static class JSONBundleConfig {
        @JsonProperty("UHF")
        private List<SolverConfig> UHF = new ArrayList<>();
        @JsonProperty("VHF")
        private List<SolverConfig> VHF = new ArrayList<>();

    }

    public interface SolverConfig {
        ISolver createSolver(SATFCContext context, ISolver solverToDecorate);
        default ISolver createSolver(SATFCContext context) {
            return createSolver(context, new VoidSolver());
        }
        default boolean shouldSkip(SATFCContext context) { return false; }
    }

    public static abstract class CacheSolverConfig implements SolverConfig {

        @Override
        public boolean shouldSkip(SATFCContext context) {
            return context.getServerURL() == null;
        }
    }

    public interface IStationPackingConfigurationStrategyConfig {
        IStationPackingConfigurationStrategy createStrategy();
    }

    public interface IStationAddingStrategyConfig {
        IStationAddingStrategy createStrategy();
    }

    @Builder
    @Data
    public static class SATFCContext {
        private final String serverURL;
        private final Clasp3LibraryGenerator clasp3LibraryGenerator;
        private final UBCSATLibraryGenerator ubcsatLibraryGenerator;
        private final ManagerBundle managerBundle;
        private final String resultFile;
    }

    @Data
    public static class ClaspConfig implements SolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            final IConstraintManager constraintManager = context.getManagerBundle().getConstraintManager();
            final Clasp3LibraryGenerator clasp3LibraryGenerator = context.getClasp3LibraryGenerator();
            final AbstractCompressedSATSolver claspSATsolver = new Clasp3SATSolver(clasp3LibraryGenerator.createLibrary(), config);
            return new CompressedSATBasedSolver(claspSATsolver, new SATCompressor(constraintManager));
        }

        private String config;
        // TODO: use
        private EncodingType encodingType;
    }

    @Data
    public static class UBCSATConfig implements SolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            final IConstraintManager constraintManager = context.getManagerBundle().getConstraintManager();
            final UBCSATLibraryGenerator ubcsatLibraryGenerator = context.getUbcsatLibraryGenerator();
            final AbstractCompressedSATSolver ubcsatSolver = new UBCSATSolver(ubcsatLibraryGenerator.createLibrary(), config);
            return new CompressedSATBasedSolver(ubcsatSolver, new SATCompressor(constraintManager));
        }

        private String config;
        // TODO: use
        private EncodingType encodingType;
    }

    @Data
    public static class AssignmentVerifierConfig implements SolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new AssignmentVerifierDecorator(solverToDecorate, context.getManagerBundle().getConstraintManager());
        }

    }

    @Data
    public static class ConnectedComponentsConfig implements SolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new ConnectedComponentGroupingDecorator(solverToDecorate, new ConstraintGrouper(), context.getManagerBundle().getConstraintManager());
        }
    }

    @Data
    public static class ArcConsistencyConfig implements SolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new ArcConsistencyEnforcerDecorator(solverToDecorate, context.getManagerBundle().getConstraintManager());
        }
    }

    @Data
    public static class CacheConfig extends CacheSolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new CacheResultDecorator(solverToDecorate, new CacherProxy(context.getServerURL()), context.getManagerBundle().getCacheCoordinate());
        }

    }

    @Data
    public static class SATCacheConfig extends CacheSolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new SupersetCacheSATDecorator(solverToDecorate, new ContainmentCacheProxy(context.getServerURL(), context.getManagerBundle().getCacheCoordinate()));
        }

    }

    @Data
    public static class UNSATCacheConfig extends CacheSolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new SubsetCacheUNSATDecorator(solverToDecorate, new ContainmentCacheProxy(context.getServerURL(), context.getManagerBundle().getCacheCoordinate()));
        }

    }

    @Data
    public static class UnderconstrainedConfig implements SolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new UnderconstrainedStationRemoverSolverDecorator(solverToDecorate, context.getManagerBundle().getConstraintManager(), new HeuristicUnderconstrainedStationFinder(context.getManagerBundle().getConstraintManager(), expensive), recursive);
        }

        private boolean expensive;
        private boolean recursive;
    }

    @Data
    public static class ResultSaverConfig implements SolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new ResultSaverSolverDecorator(solverToDecorate, context.getResultFile());
        }

        @Override
        public boolean shouldSkip(SATFCContext context) {
            return context.getResultFile() == null;
        }
    }


    @Data
    public static class SATPresolver implements SolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new ConstraintGraphNeighborhoodPresolver(solverToDecorate,
                    new StationSubsetSATCertifier(solverConfig.createSolver(context)),
                    strategy.createStrategy(),
                    context.getManagerBundle().getConstraintManager());
        };

        private SolverConfig solverConfig;
        private IStationPackingConfigurationStrategyConfig strategy;

    }

    @Data
    public static class UNSATPresolver implements SolverConfig {


        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new ConstraintGraphNeighborhoodPresolver(solverToDecorate,
                    new StationSubsetSATCertifier(solverConfig.createSolver(context)),
                    strategy.createStrategy(),
                    context.getManagerBundle().getConstraintManager());
        };


        private SolverConfig solverConfig;
        private IStationPackingConfigurationStrategyConfig strategy;

    }

    @Data
    public static class ParallelConfig implements SolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            List<ISolverFactory> solverFactories = new ArrayList<>();
            for (List<SolverConfig> configPath : configs) {
                solverFactories.add(aSolver -> concat(configPath, context));
            }
            return new ParallelNoWaitSolverComposite(solverFactories.size(), solverFactories);
        }

        private List<List<SolverConfig>> configs;

    }

    @Data
    public static class NeighbourLayerConfig implements IStationAddingStrategyConfig {

        @Override
        public IStationAddingStrategy createStrategy() {
            return new AddNeighbourLayerStrategy(numLayers);
        }

        private int numLayers;
    }

    @Data
    public static class AddRandomNeighbourConfig implements IStationAddingStrategyConfig {

        @Override
        public IStationAddingStrategy createStrategy() {
            return new AddRandomNeighboursStrategy(numNeighbours);
        }

        private int numNeighbours;
    }

    @Data
    public static class IterativeDeepeningStrategyConfig implements IStationPackingConfigurationStrategyConfig {

        @Override
        public IStationPackingConfigurationStrategy createStrategy() {
            return new IterativeDeepeningConfigurationStrategy(config.createStrategy(), baseCutoff, scalingFactor);
        }

        private double scalingFactor;
        private double baseCutoff;
        private IStationAddingStrategyConfig config;

    }

    public enum EncodingType {
        DIRECT, MULTIVALUED
    }

}
