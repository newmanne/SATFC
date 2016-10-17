package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.participation.IParticipationDecider;
import ca.ubc.cs.beta.fcc.simulator.participation.OpeningPriceHigherThanPrivateValue;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.scoring.FCCScoringRule;
import ca.ubc.cs.beta.fcc.simulator.scoring.IScoringRule;
import ca.ubc.cs.beta.fcc.simulator.solver.DistributedFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.LocalFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.IProblemGenerator;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.ProblemGeneratorImpl;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SATFCProblemSpecGeneratorImpl;
import ca.ubc.cs.beta.fcc.simulator.state.IStateSaver;
import ca.ubc.cs.beta.fcc.simulator.state.SaveStateToFile;
import ca.ubc.cs.beta.fcc.simulator.station.*;
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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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

    @Getter
    @Parameter(names = "-INFO-FILE", description = "csv file")
    private String infoFile = "/ubc/cs/research/arrow/satfc/simulator/data/station_info_v2.csv";

    @Getter
    @Parameter(names = "-VOLUMES-FILE", description = "volumes file")
    private String volumeFile = "/ubc/cs/research/arrow/satfc/simulator/data/volumes.csv";

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
    @Parameter(names = "-UNIT-VALUE", description = "Sets all stations to have unit value")
    private boolean unitValue = false;

    @Getter
    @Parameter(names = "-UHF-ONLY", description = "Ignore non-UHF stations")
    private boolean uhfOnly = false;

    @Getter
    @Parameter(names = "-UHF-TO-OFF", description = "Price per unit volume if a UHF station moves to OFF")
    private double UHFToOff = 900;

    // backwards compatibilty
    public double getBaseClockPrice() {
        return UHFToOff;
    }

    @Getter
    @Parameter(names = "-IGNORE-CANADA")
    private boolean ignoreCanada = true;

    @Parameter(names = "-SCORING-RULE")
    private ScoringRule scoringRule = ScoringRule.FCC;

    @Getter
    @Parameter(names = "-MAX-CHANNEL", description = "highest available channel")
    private int maxChannel = 29;

    @Getter
    @Parameter(names = "-CONSTRAINT-SET", description = "constraint set name (not full path!)")
    private String constraintSet = "032416SC46U";

    @Getter
    @Parameter(names = "-RESTORE-SIMULATION", description = "Restore simulation from state folder")
    private boolean restore = false;

    // TODO: go to yaml if this gets any more complicated
    @Getter
    @Parameter(names = "-START-CITY", description = "City to start from")
    private String city;
    @Getter
    @Parameter(names = "-CITY-LINKS", description = "Number of links away from start city")
    private int nLinks = -1;

    public String getStationInfoFolder() {
        return facadeParameters.fInterferencesFolder + File.separator + constraintSet;
    }

    public double getCutoff() {
        return facadeParameters.fInstanceParameters.Cutoff;
    }

    public void setUp() {
        log = LoggerFactory.getLogger(Simulator.class);
        RandomUtils.setRandom(facadeParameters.fInstanceParameters.Seed);
        BandHelper.setUHFChannels(maxChannel);
        final File outputFolder = new File(getOutputFolder());
        if (isRestore()) {
            Preconditions.checkState(outputFolder.exists() && outputFolder.isDirectory(), "Expected to restore state but no state directory found!");
        } else {
            if (outputFolder.exists()) {
                outputFolder.delete();
            }
            outputFolder.mkdirs();
            new File(getStateFolder()).mkdir();
        }

        dataManager = new DataManager();
        try {
            dataManager.addData(getStationInfoFolder());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        problemGenerator = new ProblemGeneratorImpl(getMaxChannel(), getStationManager());

        final IVolumeCalculator volumeCalculator;
        if (isUnitVolume()) {
            volumeCalculator = new UnitVolumeCalculator();
        } else {
            volumeCalculator = new CSVVolumeCalculator(volumeFile);
        }

        final IValueCalculator valueCalculator;
        if (isUnitValue()) {
            valueCalculator = new UnitValueCalculator();
        } else {
            valueCalculator = new IValueCalculator() {
                @Override
                public void setValues(Set<StationInfo> stationInfos) {
                    for (StationInfo s : stationInfos) {
                        final Map<Band, Double> values = new HashMap<>();
                        values.put(Band.OFF, 0.);
                        s.getHomeBand().getBandsBelowInclusive().stream().filter(b -> b.isAbove(Band.OFF)).sorted(Comparator.comparingInt(b -> b.ordinal())).forEach(b -> {
//                            values.put()
                        });
                        s.setValues(values);
                    }
                }
            };
        }

        final List<IPredicateFactory> ignorePredicateFactories = new ArrayList<>();
        ignorePredicateFactories.add(y -> x -> isIgnoreCanada() && x.getNationality().equals(Nationality.CA));
        ignorePredicateFactories.add(y -> x -> isUhfOnly() && !getStationManager().getDomain(new Station(x.getId())).stream().anyMatch(c -> c >= StationPackingUtils.UHFmin));
        if (city != null && nLinks >= 0) {
            ignorePredicateFactories.add(new CityAndLinksPrediateFactory(city, nLinks, getStationManager(), getConstraintManager()));
        }

        final Function<IStationInfo, IStationInfo> decorators = Function.identity();

        stationDB = new CSVStationDB(getInfoFile(), volumeCalculator, valueCalculator, getStationManager(), maxChannel, uhfOnly, ignorePredicateFactories, decorators);
    }

    private String getStateFolder() {
        return getOutputFolder() + File.separator + "state";
    }

    @Getter
    @ParametersDelegate
    private SATFCFacadeParameters facadeParameters = new SATFCFacadeParameters();

    public long getSeed() {
        return facadeParameters.fInstanceParameters.Seed;
    }

    @Getter
    @Parameter(names = "-SIMULATOR-OUTPUT-FOLDER", description = "output file name")
    private String outputFolder = "output";

    @Parameter(names = "-SOLVER-TYPE", description = "Type of solver")
    private SolverType solverType = SolverType.LOCAL;

    @Parameter(names = "-PARTICIPATION-MODEL", description = "Type of solver")
    private ParticipationModel participationModel = ParticipationModel.PRICE_HIGHER_THAN_VALUE;

    public IStateSaver getStateSaver() {
        return new SaveStateToFile(getStateFolder());
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
    private IProblemGenerator problemGenerator;
    @Getter
    private StationDB stationDB;

    public IFeasibilitySolver createSolver() {
        IFeasibilitySolver solver;
        switch (solverType) {
            case LOCAL:
                log.info("Using a local based solver");
                solver = new LocalFeasibilitySolver(facadeParameters);
                break;
            case DISTRIBUTED:
                solver =  new DistributedFeasibilitySolver(facadeParameters.fRedisParameters.getJedis(), sendQueue, listenQueue);
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
        PRICE_HIGHER_THAN_VALUE
    }

    public IParticipationDecider getParticipationDecider(IPrices prices) {
        switch (participationModel) {
            case PRICE_HIGHER_THAN_VALUE:
                return new OpeningPriceHigherThanPrivateValue(prices);
            default:
                throw new IllegalStateException();
        }
    }

    public enum ScoringRule {
        FCC,
    }

    public IScoringRule getScoringRule() {
        switch (scoringRule) {
            case FCC:
                return new FCCScoringRule();
            default:
                throw new IllegalStateException();
        }
    }

    public interface IPredicateFactory {

        Predicate<IStationInfo> create(Map<Integer, IStationInfo> stations);

    }

    @RequiredArgsConstructor
    public static class CityAndLinksPrediateFactory implements IPredicateFactory {

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

        void setVolumes(Set<StationInfo> stationInfo);

    }

    public static class CSVVolumeCalculator implements IVolumeCalculator {

        final ImmutableMap<Integer, Double> volumes;

        public CSVVolumeCalculator(String volumeFile) {
            log.info("Reading volumes from {}", volumeFile);
            // Parse volumes
            final ImmutableMap.Builder<Integer, Double> volumeBuilder = ImmutableMap.builder();
            final Iterable<CSVRecord> volumeRecords = SimulatorUtils.readCSV(volumeFile);
            for (CSVRecord record : volumeRecords) {
                int id = Integer.parseInt(record.get("FacID"));
                double volume = Double.parseDouble(record.get("Volume"));
                volumeBuilder.put(id, volume);
            }
            volumes = volumeBuilder.build();
            log.info("Finished reading volumes");
        }

        @Override
        public void setVolumes(Set<StationInfo> stationInfo) {
            for (StationInfo s: stationInfo) {
                s.setVolume(volumes.get(s.getId()));
            }
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

//    public static class CSVValueReader implements IValueCalculator {
//
//        public CSVValueReader() {
////            new HashMap<>();
////            final Map<String, String> recordAsMap = record.toMap();
////            for (Band b : Band.values()) {
////                if (recordAsMap.containsKey(b.toString() + "Value")) {
////                    values.put(b, Double.parseDouble(recordAsMap.get(b.toString() + "Value")) * 1e6);
////                }
////            }
////            if (values.isEmpty()) {
////                // Backwards compatibility
////                final String valueString = record.get("Value");
////                Preconditions.checkState(!valueString.isEmpty());
////                double value = Double.parseDouble(valueString) * 1e6;
////                values.put(band, value);
////            }
////            Preconditions.checkState(values.size() > 0);
////            values.put(Band.OFF, 0.);
//        }
//
//        @Override
//        public Map<Band, Double> get(int id) {
//            return null;
//        }
//
//    }



}
