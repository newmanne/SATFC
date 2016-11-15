package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.participation.IParticipationDecider;
import ca.ubc.cs.beta.fcc.simulator.participation.OpeningPriceHigherThanPrivateValue;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.participation.UniformParticipationDecider;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.scoring.FCCScoringRule;
import ca.ubc.cs.beta.fcc.simulator.scoring.IScoringRule;
import ca.ubc.cs.beta.fcc.simulator.solver.DistributedFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.LocalFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SATFCProblemSpecGeneratorImpl;
import ca.ubc.cs.beta.fcc.simulator.state.IStateSaver;
import ca.ubc.cs.beta.fcc.simulator.state.SaveStateToFile;
import ca.ubc.cs.beta.fcc.simulator.station.*;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.ISimulatorUnconstrainedChecker;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.NeverUnconstrainedStationChecker;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.SimulatorUnconstrainedCheckerImpl;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import ca.ubc.cs.beta.fcc.simulator.utils.RandomUtils;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCFacadeParameters;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.DataManager;
import ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies.AddNeighbourLayerStrategy;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import humanize.Humanize;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-05-20.
 */
@UsageTextField(title = "Simulator Parameters", description = "Simulator Parameters")
public class SimulatorParameters extends AbstractOptions {

    private static Logger log;

    private final String ARROW_DIR = "/ubc/cs/research/arrow/satfc/simulator/data/";

    @Getter
    @Parameter(names = "-INFO-FILE", description = "csv file")
    private String infoFile = ARROW_DIR + "station_info_v2.csv";

    @Getter
    @Parameter(names = "-VOLUMES-FILE", description = "volumes file")
    private String volumeFile = ARROW_DIR + "volumes.csv";


    @Getter
    @Parameter(names = "-VALUES-SEED", description = "values file")
    private int valuesSeed = 1;

    @Getter
    @Parameter(names = "-MAX-CF-STICK-FILE", description = "maxcfstick")
    private String maxCFStickFile = ARROW_DIR + "valuations.csv";

    @Getter
    @Parameter(names = "-SEND-QUEUE", description = "queue name to send work on")
    private String sendQueue = "send";
    @Getter
    @Parameter(names = "-LISTEN-QUEUE", description = "queue name to listen for work on")
    private String listenQueue = "listen";

    @Getter
    @Parameter(names = "-UNIT-VOLUME", description = "Sets all stations to have unit volume")
    private boolean unitVolume = false;


    @Getter
    @Parameter(names = "-UHF-ONLY", description = "Ignore non-UHF stations")
    private boolean uhfOnly = false;

    @Getter
    @Parameter(names = "-UHF-TO-OFF", description = "Price per unit volume if a UHF station moves to OFF")
    private double UHFToOff = 900;

    @Getter
    @Parameter(names = "-IGNORE-CANADA")
    private boolean ignoreCanada = false;

    @Parameter(names = "-SCORING-RULE")
    private ScoringRule scoringRule = ScoringRule.FCC;

    @Getter
    @Parameter(names = "-MAX-CHANNEL", description = "highest available channel")
    private Integer maxChannel = null;

    @Getter
    @Parameter(names = "-CONSTRAINT-SET", description = "constraint set name (not full path!)")
    private String constraintSet = "nov2015";

    @Getter
    @Parameter(names = "-RESTORE-SIMULATION", description = "Restore simulation from state folder")
    private boolean restore = false;

//    @Getter
//    @Parameter(names = "-VALUES-FILE", description = "values file")
//    private String valuesFile = ARROW_DIR + "values_v2.csv";

    //    @Getter
//    @Parameter(names = "-UNIT-VALUE", description = "Sets all stations to have unit value")
//    private boolean unitValue = false;

//    @Getter
//    @Parameter(names = "-START-CITY", description = "City to start from")
//    private String city;
//    @Getter
//    @Parameter(names = "-CITY-LINKS", description = "Number of links away from start city")
//    private int nLinks = -1;

//
//    @Getter
//    @Parameter(names = "-UNIFORM-PROBABILITY-PARTICIPATION", description = "A station participates uniformly with p")
//    private Double uniformProbability;

    @Getter
    @Parameter(names = "-SIMULATOR-OUTPUT-FOLDER", description = "output file name")
    private String outputFolder = "output";

    @Parameter(names = "-SOLVER-TYPE", description = "Type of solver")
    private SolverType solverType = SolverType.LOCAL;

    @Parameter(names = "-PARTICIPATION-MODEL", description = "Type of solver")
    private ParticipationModel participationModel = ParticipationModel.PRICE_HIGHER_THAN_VALUE;

    @Parameter(names = "-UNCONSTRAINED-CHECKER", description = "Type of unconstrained checker")
    private UnconstrainedChecker unconstrainedChecker = UnconstrainedChecker.FCC;


    @Parameter(names = "-UHF-CACHE", description = "If true, cache problems")
    @Getter
    private boolean UHFCache = true;

    @Parameter(names = "-LAZY-UHF-CACHE", description = "If true, do not precompute problems")
    @Getter
    private boolean lazyUHF = true;

    @Parameter(names = "-GREEDY-SOLVER-FIRST", description = "If true, always try solving a problem with the greedy solver first")
    @Getter
    private boolean greedyFirst = true;


    @Getter
    @ParametersDelegate
    private SATFCFacadeParameters facadeParameters = new SATFCFacadeParameters();

    public ISimulatorUnconstrainedChecker getUnconstrainedChecker(ParticipationRecord participation) {
        switch(unconstrainedChecker) {
            case FCC:
                return new SimulatorUnconstrainedCheckerImpl(getConstraintManager(), participation);
            case BAD:
                return new NeverUnconstrainedStationChecker();
            default:
                throw new IllegalStateException();
        }
    }

    public enum UnconstrainedChecker {
        FCC, BAD
    }

    public String getStationInfoFolder() {
        return facadeParameters.fInterferencesFolder + File.separator + constraintSet;
    }

    public double getCutoff() {
        return facadeParameters.fInstanceParameters.Cutoff;
    }

    public void setUp() {
        log = LoggerFactory.getLogger(Simulator.class);
        RandomUtils.setRandom(facadeParameters.fInstanceParameters.Seed);
        eventBus = new EventBus();
        BandHelper.setUHFChannels(maxChannel != null ? maxChannel : StationPackingUtils.UHFmax);
        final File outputFolder = new File(getOutputFolder());
        if (isRestore()) {
            Preconditions.checkState(outputFolder.exists() && outputFolder.isDirectory(), "Expected to restore state but no state directory found!");
        } else {
            if (outputFolder.exists()) {
                outputFolder.delete();
            }
            outputFolder.mkdirs();
            new File(getStateFolder()).mkdir();
            new File(getProblemFolder()).mkdir();
        }

        dataManager = new DataManager();
        try {
            dataManager.addData(getStationInfoFolder());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        stationDB = new CSVStationDB(getInfoFile(), getStationManager());

        final Set<Integer> toRemove = new HashSet<>();
        for (IStationInfo s : stationDB.getStations()) {
            if (isIgnoreCanada() && s.getNationality().equals(Nationality.CA)) {
                log.info("Station {} is a Canadian station and ignore Canada flag is set to true", s);
                toRemove.add(s.getId());
            } else if (isUhfOnly() && s.getDomain(Band.UHF).isEmpty()) {
                log.info("Station {} is not a UHF station and the UHF only flag is set to true", s);
                toRemove.add(s.getId());
            }
//            if (city != null && nLinks >= 0) {
//                ignorePredicateFactories.add(new CityAndLinksPredicateFactory(city, nLinks, getStationManager(), getConstraintManager()));
//            }
        }
        toRemove.forEach(stationDB::removeStation);

        log.info("Setting volumes");
        IVolumeCalculator volumeCalculator;
        if (isUnitVolume()) {
            volumeCalculator = new UnitVolumeCalculator();
        } else {
            volumeCalculator = new CSVVolumeCalculator(volumeFile);
        }
        final Set<IStationInfo> americanStations = Sets.newHashSet(stationDB.getStations(Nationality.US));
        final Map<Integer, Integer> volumes = volumeCalculator.getVolumes(americanStations);
        for (IStationInfo stationInfo : americanStations) {
            int volume = volumes.get(stationInfo.getId());
            ((StationInfo) stationInfo).setVolume(volume);
        }


        // TOOD: This might make sense to do if you ever calculate volumes, but for now use FCC
//        volumeCalculator = new NormalizingVolumeDecorator(volumeCalculator);

    }

    private String getStateFolder() {
        return getOutputFolder() + File.separator + "state";
    }

    public String getProblemFolder() {
        return getOutputFolder() + File.separator + "problems";
    }


    public long getSeed() {
        return facadeParameters.fInstanceParameters.Seed;
    }

    public IStateSaver getStateSaver() {
        return new SaveStateToFile(getStateFolder(), getEventBus());
    }


    public IStationManager getStationManager() {
        try {
            return dataManager.getData(getStationInfoFolder()).getStationManager();
        } catch (FileNotFoundException e) {
            throw new IllegalStateException();
        }
    }

    public IConstraintManager getConstraintManager() {
        try {
            return dataManager.getData(getStationInfoFolder()).getConstraintManager();
        } catch (FileNotFoundException e) {
            throw new IllegalStateException();
        }
    }

    private DataManager dataManager;
    @Getter
    private IStationDB.IModifiableStationDB stationDB;
    @Getter
    private EventBus eventBus;

    public IFeasibilitySolver createSolver() {
        IFeasibilitySolver solver;
        switch (solverType) {
            case LOCAL:
                log.info("Using a local based solver");
                solver = new LocalFeasibilitySolver(facadeParameters);
                break;
            case DISTRIBUTED:
                solver = new DistributedFeasibilitySolver(facadeParameters.fRedisParameters.getJedis(), sendQueue, listenQueue);
                break;
            default:
                throw new IllegalStateException();
        }
        return solver;
    }

    public Simulator.ISATFCProblemSpecGenerator createProblemSpecGenerator() {
        return new SATFCProblemSpecGeneratorImpl(getStationInfoFolder(), getCutoff(), getSeed());
    }

    public enum ParticipationModel {
        PRICE_HIGHER_THAN_VALUE,
        UNIFORM
    }

    public IParticipationDecider getParticipationDecider(IPrices prices) {
        switch (participationModel) {
            case PRICE_HIGHER_THAN_VALUE:
                return new OpeningPriceHigherThanPrivateValue(prices);
//            case UNIFORM:
//                return new UniformParticipationDecider(uniformProbability, prices);
            default:
                throw new IllegalStateException();
        }
    }

    public enum ScoringRule {
        FCC,
    }

    public interface IPredicateFactory {

        Predicate<IStationInfo> create(Map<Integer, IStationInfo> stations);

    }

    // TODO: Hard to ratify this with endogenous clearing targets...
    @RequiredArgsConstructor
    public static class CityAndLinksPredicateFactory implements IPredicateFactory {

        private final String city;
        private final int links;
        private final IStationManager stationManager;
        private final IConstraintManager constraintManager;

        @Override
        public Predicate<IStationInfo> create(Map<Integer, IStationInfo> stations) {
            final Map<Station, Set<Integer>> domains = stationManager.getStations()
                    .stream()
                    .filter(s -> stations.get(s.getID()) != null)
                    .collect(Collectors.toMap(s -> s, s -> stations.get(s.getID()).getDomain()));

            final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, constraintManager);

            final Set<Station> cityStations = domains.keySet().stream()
                    .filter(s -> stations.get(s.getID()).getCity().equals(city))
                    .collect(Collectors.toSet());

            log.info("Found {} stations in city {}", cityStations.size(), city);
            Preconditions.checkState(cityStations.size() > 0, "No stations found in %s", city);

            final Iterator<Set<Station>> stationsToPack = new AddNeighbourLayerStrategy().getStationsToPack(constraintGraph, cityStations).iterator();
            final Set<Station> output = Sets.newHashSet(cityStations);
            for (int i = 0; i < links; i++) {
                if (stationsToPack.hasNext()) {
                    output.addAll(stationsToPack.next());
                } else {
                    log.info("Exhausted all stations");
                }
            }

            log.info("Found {} stations within {} links of {}", output.size(), links, city);

            return (x -> !output.contains(x.toSATFCStation()));
        }
    }

    public interface IVolumeCalculator {

        Map<Integer, Integer> getVolumes(Set<IStationInfo> stations);

    }

    public static class CSVVolumeCalculator implements IVolumeCalculator {

        final ImmutableMap<Integer, Integer> volumes;

        public CSVVolumeCalculator(String volumeFile) {
            log.info("Reading volumes from {}", volumeFile);
            // Parse volumes
            final ImmutableMap.Builder<Integer, Integer> volumeBuilder = ImmutableMap.builder();
            final Iterable<CSVRecord> volumeRecords = SimulatorUtils.readCSV(volumeFile);
            for (CSVRecord record : volumeRecords) {
                int id = Integer.parseInt(record.get("FacID"));
                int volume = Integer.parseInt(record.get("Volume"));
                volumeBuilder.put(id, volume);
            }
            volumes = volumeBuilder.build();
            log.info("Finished reading volumes");
        }

        @Override
        public Map<Integer, Integer> getVolumes(Set<IStationInfo> stations) {
            return volumes;
        }
    }

    @RequiredArgsConstructor
    public static class NormalizingVolumeDecorator implements IVolumeCalculator {

        private final IVolumeCalculator decorated;

        @Override
        public Map<Integer, Integer> getVolumes(Set<IStationInfo> stations) {
            final Map<Integer, Integer> volumes = decorated.getVolumes(stations);
            final Map<Integer, Integer> normalized = new HashMap<>();

            double max = volumes.values().stream().mapToDouble(x->x).max().getAsDouble();
            for (Map.Entry<Integer, Integer> entry : volumes.entrySet()) {
                normalized.put(entry.getKey(), (int) Math.round((entry.getValue() / max) * 1e6));
            }
            return normalized;
        }
    }

//    public class FCCVolumeCalculator implements IVolumeCalculator {
//
//        @Override
//        public void setVolumes(Set<StationInfo> stationInfo) {
//            final Map<StationInfo, Double> nonNormlalizedVolumes = new HashMap<>();
//            final IStationManager stationManager = getStationManager();
//            final IConstraintManager constraintManager = getConstraintManager();
//            log.debug("There are {} stations in the manager", stationManager.getStations().size());
//            final Map<Station, Set<Integer>> domains = stationManager.getStations()
//                    .stream()
//                    .filter(s -> stationDB.getStationById(s.getID()) != null)
//                    .collect(Collectors.toMap(s -> s, s -> stationDB.getStationById(s.getID()).getDomain()));
//            final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, getConstraintManager());
//            final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(constraintGraph);
//            final Map<Station, Integer> icMap = new HashMap<>();
//            for (Station a : domains.keySet()) {
//                int icNum = 0;
//                for (Station b : neighborIndex.neighborsOf(a)) {
//                    int overallChanMax = 0;
//                    for (Integer chanA : domains.get(a)) {
//                        int chanMax = 0;
//                        for (Integer chanB : domains.get(b)) {
//                            if (!constraintManager.isSatisfyingAssignment(a, chanA, b, chanB)) {
//                                chanMax += 1;
//                            }
//                        }
//                        overallChanMax = Math.max(overallChanMax, chanMax);
//                    }
//                    icNum += overallChanMax;
//                }
//                icMap.put(a, icNum);
//            }
//
//            for (StationInfo s: stationInfo) {
//                int pop = s.getPopulation();
//
//            }
//
//        }
//    }

    public interface IValueCalculator {

        void setValues(Set<StationInfo> stationInfos);

    }

    @Slf4j
    public static class CSVValueReader implements IValueCalculator {

        private final Map<Integer, Double> values;

        public CSVValueReader(final String infoFile) {
            log.info("Reading station values from {}", infoFile);
            values = new HashMap<>();
            final Iterable<CSVRecord> records = SimulatorUtils.readCSV(infoFile);
            for (CSVRecord record : records) {
                final int id = Integer.parseInt(record.get("FacID"));
                final String valueString = record.get("Value");
                Preconditions.checkState(!valueString.isEmpty());
                double value = Double.parseDouble(valueString) * 1e6;
                log.info("Value of {} for {}", Humanize.spellBigNumber(value), id);
                values.put(id, value);
            }
        }

        @Override
        public void setValues(Set<StationInfo> stationInfos) {
            for (StationInfo station : stationInfos) {
                Double value = values.get(station.getId());
                Preconditions.checkNotNull(value, "No value for station %s", station);
            }
        }

        public boolean hasValue(int id) {
            return values.containsKey(id);
        }

    }

}
