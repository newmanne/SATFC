package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ca.ubc.cs.beta.stationpacking.facade.SATFCFacade;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeParameter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Builder;
import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.Clasp3LibraryGenerator;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.PythonInterpreterFactory;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories.UBCSATLibraryGenerator;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.VoidSolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.ConstraintGraphNeighborhoodPresolver;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.StationSubsetSATCertifier;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.AddNeighbourLayerStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.AddRandomNeighboursStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.IStationAddingStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.IStationPackingConfigurationStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.IterativeDeepeningConfigurationStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ISolverFactory;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ParallelNoWaitSolverComposite;
import ca.ubc.cs.beta.stationpacking.solvers.composites.ParallelSolverComposite;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.AssignmentVerifierDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.CNFSaverSolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ConnectedComponentGroupingDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.PythonAssignmentVerifierDecorator;
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
import ca.ubc.cs.beta.stationpacking.utils.YAMLUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.Yaml;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.Resources;

/**
 * Created by newmanne on 01/10/15.
 * Builds a bundle based on a JSON description
 */
@Slf4j
public class YAMLBundle extends AVHFUHFSolverBundle {

    @Getter
    private final ISolver UHFSolver;
    @Getter
    private final ISolver VHFSolver;

    public YAMLBundle(
            @NonNull ManagerBundle managerBundle,
            SATFCFacadeParameter parameter
    ) {
        super(managerBundle);

        log.info("Using the following variables to build the bundle: configFile={}, serverURL={}, clasp={}, ubcsat={}, resultFile={}", parameter.getConfigFile(), parameter.getServerURL(), parameter.getClaspLibrary(), parameter.getUbcsatLibrary(), parameter.getResultFile());

        final SATFCContext context = SATFCContext
                .builder()
                .managerBundle(managerBundle)
                .clasp3LibraryGenerator(new Clasp3LibraryGenerator(parameter.getClaspLibrary()))
                .ubcsatLibraryGenerator(new UBCSATLibraryGenerator(parameter.getUbcsatLibrary()))
                .parameter(parameter)
                .python(new PythonInterpreterFactory(managerBundle.getInterferenceFolder(), managerBundle.isCompactInterference()))
                .build();

        log.info("Reading configuration file {}", parameter.getConfigFile());
        final String configJSONString = parameter.getConfigFile().getFileAsString();

        log.info("Running configuration file through YAML parser to handle anchors...");

        // Need to do this to handle anchors properly
        
        final Yaml yaml = new Yaml();
        Map<?, ?> normalized = (Map<?, ?>) yaml.load(configJSONString);
        String fixed = null;
        try {
            fixed = YAMLUtils.getMapper().writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Couldn't parse JSON from file " + parameter.getConfigFile(), e);
        }

        log.info("Parsing configuration file {}", parameter.getConfigFile());

        final JSONBundleConfig config;
        try {
            config = YAMLUtils.getMapper().readValue(fixed, JSONBundleConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't parse JSON from file " + parameter.getConfigFile(), e);
        }

        log.info("Configuration parsed! Building solvers...");
        final List<ISolverConfig> uhf = config.getUHF();
        final List<ISolverConfig> vhf = config.getVHF();
        Preconditions.checkState(uhf != null && !uhf.isEmpty(), "No solver provided for UHF in config file %s", parameter.getConfigFile());
        Preconditions.checkState(vhf != null && !vhf.isEmpty(), "No solver provided for VHF in config file %s", parameter.getConfigFile());

        UHFSolver = concat(uhf, context);
        VHFSolver = concat(vhf, context);
    }

    private static ISolver concat(List<ISolverConfig> configs, SATFCContext context) {
        log.debug("Starting with a void solver...");
        ISolver solver = new VoidSolver();
        for (ISolverConfig config : configs) {
            if (!config.shouldSkip(context)) {
                solver = config.createSolver(context, solver);
                log.debug("Decorating with {} using config of type {}", solver.getClass().getSimpleName(), config.getClass().getSimpleName());
            } else {
                log.debug("Skipping decorator {}", config.getClass().getSimpleName());
            }
        }
        return solver;
    }

    @Data
    public static class ConfigFile {

        final String fileName;
        final boolean internal;

        public String getFileAsString() {
            try {
                if (internal) {
                    return Resources.toString(Resources.getResource("bundles" + File.separator + fileName + ".yaml"), Charsets.UTF_8);
                } else {
                    return Files.toString(new File(fileName), Charsets.UTF_8);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not load in config file", e);
            }
        }

    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JSONBundleConfig {
        @JsonProperty("UHF")
        private List<ISolverConfig> UHF;
        @JsonProperty("VHF")
        private List<ISolverConfig> VHF;

    }

    public interface ISolverConfig {
        ISolver createSolver(SATFCContext context, ISolver solverToDecorate);
        default ISolver createSolver(SATFCContext context) {
            return createSolver(context, new VoidSolver());
        }
        default boolean shouldSkip(SATFCContext context) { return false; }
    }

    public static abstract class CacheSolverConfig implements ISolverConfig {

        @Override
        public boolean shouldSkip(SATFCContext context) {
            return context.getParameter().getServerURL() == null;
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
        private final Clasp3LibraryGenerator clasp3LibraryGenerator;
        private final UBCSATLibraryGenerator ubcsatLibraryGenerator;
        private final ManagerBundle managerBundle;
        private final PythonInterpreterFactory python;
        private final SATFCFacadeParameter parameter;
    }

    @Data
    public static class ClaspConfig implements ISolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            final IConstraintManager constraintManager = context.getManagerBundle().getConstraintManager();
            final Clasp3LibraryGenerator clasp3LibraryGenerator = context.getClasp3LibraryGenerator();
            final AbstractCompressedSATSolver claspSATsolver = new Clasp3SATSolver(clasp3LibraryGenerator.createLibrary(), config, seedOffset);
            return new CompressedSATBasedSolver(claspSATsolver, new SATCompressor(constraintManager, encodingType));
        }

        private String config;
        private EncodingType encodingType = EncodingType.DIRECT;
        private int seedOffset = 0;
    }

    @Data
    public static class UBCSATConfig implements ISolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            final IConstraintManager constraintManager = context.getManagerBundle().getConstraintManager();
            final UBCSATLibraryGenerator ubcsatLibraryGenerator = context.getUbcsatLibraryGenerator();
            final AbstractCompressedSATSolver ubcsatSolver = new UBCSATSolver(ubcsatLibraryGenerator.createLibrary(), config, seedOffset);
            return new CompressedSATBasedSolver(ubcsatSolver, new SATCompressor(constraintManager, encodingType));
        }

        private String config;
        private EncodingType encodingType = EncodingType.DIRECT;
        private int seedOffset = 0;
    }

    @Data
    public static class AssignmentVerifierConfig implements ISolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new AssignmentVerifierDecorator(solverToDecorate, context.getManagerBundle().getConstraintManager(), context.getManagerBundle().getStationManager());
        }

    }

    @Data
    public static class PythonVerifieConfig implements ISolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new PythonAssignmentVerifierDecorator(solverToDecorate, context.getPython());
        }
    }

    @Data
    public static class ConnectedComponentsConfig implements ISolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new ConnectedComponentGroupingDecorator(solverToDecorate, new ConstraintGrouper(), context.getManagerBundle().getConstraintManager());
        }
    }

    @Data
    public static class ArcConsistencyConfig implements ISolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new ArcConsistencyEnforcerDecorator(solverToDecorate, context.getManagerBundle().getConstraintManager());
        }
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class CacheConfig extends CacheSolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            final SATFCFacadeParameter parameter = context.getParameter();
            return new CacheResultDecorator(solverToDecorate, new ContainmentCacheProxy(parameter.getServerURL(), context.getManagerBundle().getCacheCoordinate(), parameter.getNumServerAttempts(), parameter.isNoErrorOnServerUnavailable()));
        }

    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class SATCacheConfig extends CacheSolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            final SATFCFacadeParameter parameter = context.getParameter();
            return new SupersetCacheSATDecorator(solverToDecorate, new ContainmentCacheProxy(parameter.getServerURL(), context.getManagerBundle().getCacheCoordinate(), parameter.getNumServerAttempts(), parameter.isNoErrorOnServerUnavailable()));
        }

    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class UNSATCacheConfig extends CacheSolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            final SATFCFacadeParameter parameter = context.getParameter();
            return new SubsetCacheUNSATDecorator(solverToDecorate, new ContainmentCacheProxy(parameter.getServerURL(), context.getManagerBundle().getCacheCoordinate(), parameter.getNumServerAttempts(), parameter.isNoErrorOnServerUnavailable()));
        }

    }

    @Data
    public static class UnderconstrainedConfig implements ISolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new UnderconstrainedStationRemoverSolverDecorator(solverToDecorate, context.getManagerBundle().getConstraintManager(), new HeuristicUnderconstrainedStationFinder(context.getManagerBundle().getConstraintManager(), expensive), recursive);
        }

        private boolean expensive = true;
        private boolean recursive = true;
    }

    @Data
    public static class ResultSaverConfig implements ISolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new ResultSaverSolverDecorator(solverToDecorate, context.getParameter().getResultFile());
        }

        @Override
        public boolean shouldSkip(SATFCContext context) {
            return context.getParameter().getResultFile() == null;
        }
    }


    @Data
    public static class SATPresolver implements ISolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new ConstraintGraphNeighborhoodPresolver(solverToDecorate,
                    new StationSubsetSATCertifier(solverConfig.createSolver(context)),
                    strategy.createStrategy(),
                    context.getManagerBundle().getConstraintManager());
        };

        private ISolverConfig solverConfig;
        private IStationPackingConfigurationStrategyConfig strategy;

    }

    @Data
    public static class UNSATPresolver implements ISolverConfig {


        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new ConstraintGraphNeighborhoodPresolver(solverToDecorate,
                    new StationSubsetSATCertifier(solverConfig.createSolver(context)),
                    strategy.createStrategy(),
                    context.getManagerBundle().getConstraintManager());
        };


        private ISolverConfig solverConfig;
        private IStationPackingConfigurationStrategyConfig strategy;

    }

    @Data
    public static class ParallelConfig implements ISolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            log.debug("Adding a parallel split");
            List<ISolverFactory> solverFactories = new ArrayList<>();
            for (final List<ISolverConfig> configPath : configs) {
                solverFactories.add(aSolver -> concat(configPath, context));
            }
            if (wait) {
                return new ParallelSolverComposite(solverFactories.size(), solverFactories);
            } else {
                return new ParallelNoWaitSolverComposite(solverFactories.size(), solverFactories);
            }
        }

        private boolean wait = false;
        private List<List<ISolverConfig>> configs;

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

    @Data
    public static class CNFSaverConfig implements ISolverConfig {

        @Override
        public ISolver createSolver(SATFCContext context, ISolver solverToDecorate) {
            return new CNFSaverSolverDecorator(solverToDecorate, context.getManagerBundle().getConstraintManager(), context.getParameter().getCNFSaver(), encodingType, true);
        }

        @Override
        public boolean shouldSkip(SATFCContext context) {
            return context.getParameter().getCNFSaver() == null;
        }

        EncodingType encodingType = EncodingType.DIRECT;
        
    }

    /**
     * Terminology from Local Search on SAT-Encoded Colouring Problems, Steven Prestwich, http://www.cs.sfu.ca/CourseCentral/827/havens/papers/topic%236(SAT)/steve1.pdf
     */
    public enum EncodingType {
        DIRECT, MULTIVALUED
    }

    public enum SolverType {
        CLASP,
        UBCSAT,
        SAT_PRESOLVER,
        UNSAT_PRESOLVER,
        UNDERCONSTRAINED,
        CONNECTED_COMPONENTS,
        ARC_CONSISTENCY,
        PYTHON_VERIFIER,
        VERIFIER,
        CACHE,
        SAT_CACHE,
        UNSAT_CACHE,
        PARALLEL,
        RESULT_SAVER,
        CNF, NONE
    }

    public enum PresolverExpansion {
        NEIGHBOURHOOD, UNIFORM_RANDOM
    }

    public enum TimingChoice {
        ITERATIVE_DEEPEN
    }

    public static abstract class ANameArgsDeserializer<KEYTYPE extends Enum<KEYTYPE>, CLASSTYPE> extends JsonDeserializer<CLASSTYPE> {

        @Override
        public CLASSTYPE deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            JsonNode root = p.readValueAsTree();
            Preconditions.checkState(root.has("name"), "name is a required field!");
            final String name = root.get("name").asText();
            final KEYTYPE enumValue = stringToEnum(name);
            final Class<? extends CLASSTYPE> targetClass = getMap().get(enumValue);
            Preconditions.checkNotNull(targetClass, "No known conversion class for type %s", enumValue);
            if (root.has("args")) {
                return YAMLUtils.getMapper().treeToValue(root.get("args"), targetClass);
            } else {
                try {
                    return targetClass.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException("Failed to instantiate an instance of " + targetClass.getSimpleName(), e);
                }
            }
        }

        protected abstract KEYTYPE stringToEnum(String string);

        protected abstract Map<KEYTYPE, Class<? extends CLASSTYPE>> getMap();
    }

    public static class SolverConfigDeserializer extends ANameArgsDeserializer<SolverType, ISolverConfig> {

        private final Map<SolverType, Class<? extends ISolverConfig>> typeToConfigClass =
                ImmutableMap.<SolverType, Class<? extends ISolverConfig>>builder()
                        .put(SolverType.CLASP, YAMLBundle.ClaspConfig.class)
                        .put(SolverType.UBCSAT, YAMLBundle.UBCSATConfig.class)
                        .put(SolverType.SAT_PRESOLVER, YAMLBundle.SATPresolver.class)
                        .put(SolverType.UNSAT_PRESOLVER, YAMLBundle.UNSATPresolver.class)
                        .put(SolverType.UNDERCONSTRAINED, YAMLBundle.UnderconstrainedConfig.class)
                        .put(SolverType.CONNECTED_COMPONENTS, YAMLBundle.ConnectedComponentsConfig.class)
                        .put(SolverType.ARC_CONSISTENCY, YAMLBundle.ArcConsistencyConfig.class)
                        .put(SolverType.VERIFIER, YAMLBundle.AssignmentVerifierConfig.class)
                        .put(SolverType.CACHE, YAMLBundle.CacheConfig.class)
                        .put(SolverType.SAT_CACHE, YAMLBundle.SATCacheConfig.class)
                        .put(SolverType.UNSAT_CACHE, YAMLBundle.UNSATCacheConfig.class)
                        .put(SolverType.PARALLEL, YAMLBundle.ParallelConfig.class)
                        .put(SolverType.RESULT_SAVER, YAMLBundle.ResultSaverConfig.class)
                        .put(SolverType.CNF, YAMLBundle.CNFSaverConfig.class)
                        .put(SolverType.PYTHON_VERIFIER, PythonVerifieConfig.class)
                        .build();

        @Override
        protected SolverType stringToEnum(String string) {
            return SolverType.valueOf(string);
        }

        @Override
        protected Map<SolverType, Class<? extends ISolverConfig>> getMap() {
            return typeToConfigClass;
        }

    }

    public static class PresolverConfigurationDeserializer extends ANameArgsDeserializer<TimingChoice, IStationPackingConfigurationStrategyConfig> {

        private final Map<TimingChoice, Class<? extends YAMLBundle.IStationPackingConfigurationStrategyConfig>> typeToConfigClass = ImmutableMap.<TimingChoice, Class<? extends YAMLBundle.IStationPackingConfigurationStrategyConfig>>builder()
                .put(TimingChoice.ITERATIVE_DEEPEN, YAMLBundle.IterativeDeepeningStrategyConfig.class)
                .build();

        @Override
        protected TimingChoice stringToEnum(String string) {
            return TimingChoice.valueOf(string);
        }

        @Override
        protected Map<TimingChoice, Class<? extends IStationPackingConfigurationStrategyConfig>> getMap() {
            return typeToConfigClass;
        }

    }

    public static class StationAddingStrategyConfigurationDeserializer extends ANameArgsDeserializer<PresolverExpansion, IStationAddingStrategyConfig> {

        private final Map<PresolverExpansion, Class<? extends YAMLBundle.IStationAddingStrategyConfig>> typeToConfigClass = ImmutableMap.<PresolverExpansion, Class<? extends YAMLBundle.IStationAddingStrategyConfig>>builder()
                .put(PresolverExpansion.NEIGHBOURHOOD, YAMLBundle.NeighbourLayerConfig.class)
                .put(PresolverExpansion.UNIFORM_RANDOM, YAMLBundle.AddRandomNeighbourConfig.class)
                .build();

        @Override
        protected PresolverExpansion stringToEnum(String string) {
            return PresolverExpansion.valueOf(string);
        }

        @Override
        protected Map<PresolverExpansion, Class<? extends IStationAddingStrategyConfig>> getMap() {
            return typeToConfigClass;
        }

    }

}