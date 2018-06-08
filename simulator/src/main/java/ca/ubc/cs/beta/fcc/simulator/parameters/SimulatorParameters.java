package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.participation.IParticipationDecider;
import ca.ubc.cs.beta.fcc.simulator.participation.OpeningOffPriceHigherThanPrivateValue;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
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
import com.google.common.io.Resources;
import humanize.Humanize;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-05-20.
 */
@UsageTextField(title = "Simulator Parameters", description = "Simulator Parameters")
public class SimulatorParameters extends AbstractOptions {

    private static Logger log;

    private String getInternalFile(String filename) {
        return new File(Paths.get(".").toAbsolutePath().normalize().toString()).getParentFile().getAbsolutePath() + File.separator + "simulator_data" + File.separator + filename;
    }

    @Parameter(names = "-INFO-FILE", description = "csv file with headers FacID,Call,Country,Channel,City,Lat,Lon,Population,DMA,Eligible")
    private String infoFile;

    public String getInfoFile() {
        return infoFile != null ? infoFile : getInternalFile("station_info.csv");
    }

    @Parameter(names = "-VOLUMES-FILE", description = "volumes csv file headers FacID, Volume")
    private String volumeFile;

    public String getVolumeFile() {
        return volumeFile != null ? volumeFile : getInternalFile("volumes.csv");
    }

    @Getter
    @Parameter(names = "-VALUES-SEED", description = "values file")
    private int valuesSeed = 1;

    // TODO: Use an env var, this only makes sense for you...
    @Getter
    @Parameter(names = "-MAX-CF-STICK-FILE", description = "maxcfstick")
    private String maxCFStickFile = "/ubc/cs/research/arrow/satfc/simulator/data/valuations.csv";

    @Getter
    @Parameter(names = "-VALUE-FILE", description = "CSV file with station value in each band for each American station (FacID, UHFValue, HVHFValue, LVHFValue)")
    private String valueFile;

    @Parameter(names = "-COMMERCIAL-FILE", description = "CSV file with whether eligible stations are commercial non-commercial (FacID, Commercial)")
    private String commercialFile;

    public String getCommercialFile() {
        return commercialFile != null ? commercialFile : getInternalFile("commercial.csv");
    }


    @Getter
    @Parameter(names = "-STARTING-ASSIGNMENT-FILE", description = "CSV file with columns Ch, FacID specifying a starting assignment for non-participating stations")
    private String startingAssignmentFile;


    @Getter
    @Parameter(names = "-SEND-QUEUE", description = "queue name to send work on")
    private String sendQueue = "send";
    @Getter
    @Parameter(names = "-LISTEN-QUEUE", description = "queue name to listen for work on")
    private String listenQueue = "listen";

    @Getter
    @Parameter(names = "-UHF-ONLY", description = "Ignore non-UHF stations")
    private boolean uhfOnly = false;

    @Getter
    @Parameter(names = "-UHF-TO-OFF", description = "Price per unit volume if a UHF station moves to OFF")
    private double UHFToOff = 900;

    @Getter
    @Parameter(names = "-INCLUDE-VHF", description = "Include the VHF bands in the auctions")
    private boolean includeVHFBands = true;

    @Getter
    @Parameter(names = "-IGNORE-CANADA")
    private boolean ignoreCanada = false;

    @Getter
    @Parameter(names = "-MAX-CHANNEL", description = "highest available channel")
    private Integer maxChannel = null;

    @Getter
    @Parameter(names = "-CONSTRAINT-SET", description = "constraint set name (not full path!)")
    private String constraintSet = "nov2015";

    @Getter
    @Parameter(names = "-RESTORE-SIMULATION", description = "Restore simulation from state folder")
    private boolean restore = false;


    @Getter
    @Parameter(names = "-START-CITY", description = "City to start from")
    private String city;
    @Getter
    @Parameter(names = "-CITY-LINKS", description = "Number of links away from start city")
    private int nLinks = 0;

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

    @Getter
    @Parameter(names = "-REVISIT-TIMEOUTS", description = "If true, revisit timeout results")
    private boolean revisitTimeouts = false;

    @Parameter(names = "-GREEDY-SOLVER-FIRST", description = "If true, always try solving a problem with the greedy solver first")
    @Getter
    private boolean greedyFirst = true;

    @Parameter(names = "-GREEDY-SOLVER-ONLY", description = "If true, don't use SATFC after init")
    @Getter
    private boolean greedyOnly = false;

    public enum BidProcessingAlgorithm {
        FCC, FIRST_TO_FINISH
    }

    @Getter
    @Parameter(names = "-BID-PROCESSING", description = "Which bid processing algorithm to use")
    private BidProcessingAlgorithm bidProcessingAlgorithm = BidProcessingAlgorithm.FCC;

    @Parameter(names = "-NOISE-STD", description = "Noise to add to 1/3, 2/3")
    @Getter
    private double noiseStd = 0.05;

    @Parameter(names = "-PARALLELISM", description = "Max threads to run for MIP solving")
    @Getter
    private int parallelism = Runtime.getRuntime().availableProcessors();

    @Parameter(names = "-MIP-CUTOFF", description = "Number of seconds to run initial MIP")
    @Getter
    private double mipCutoff = 60 * 60;

    @Parameter(names = "-STORE-PROBLEMS", description = "Write every problem to disk")
    @Getter
    private boolean storeProblems = false;


    @Getter
    @ParametersDelegate
    private SATFCFacadeParameters facadeParameters = new SATFCFacadeParameters();

    public String getInteferenceFolder() {
        return facadeParameters.fInterferencesFolder != null ? facadeParameters.fInterferencesFolder : getInternalFile("interference_data");
    }

    public ISimulatorUnconstrainedChecker getUnconstrainedChecker(ParticipationRecord participation, ILadder ladder) {
        switch (unconstrainedChecker) {
            case FCC:
                return new SimulatorUnconstrainedCheckerImpl(getConstraintManager(), participation, ladder);
            case BAD:
                return new NeverUnconstrainedStationChecker();
            default:
                throw new IllegalStateException();
        }
    }

    public List<Band> getAuctionBands() {
        return isIncludeVHFBands() ? Arrays.asList(Band.values()) : Arrays.asList(Band.OFF, Band.UHF);
    }

    public enum UnconstrainedChecker {
        FCC, BAD
    }

    public String getStationInfoFolder() {
        return getInteferenceFolder() + File.separator + constraintSet;
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
            if (s.getNationality().equals(Nationality.US) && !s.isEligible()) {
                log.info("Station {} is a US station that was not offered an opening price, meaning it must be Not Needed and can effectively be ignored. Excluding from auction", s);
                toRemove.add(s.getId());
            }
            if (isIgnoreCanada() && s.getNationality().equals(Nationality.CA)) {
                log.info("Station {} is a Canadian station and ignore Canada flag is set to true", s);
                toRemove.add(s.getId());
            } else if ((isUhfOnly() || !isIncludeVHFBands()) && s.getDomain(Band.UHF).isEmpty()) {
                log.info("Station {} has no domain in UHF, skipping due to flag", s);
                toRemove.add(s.getId());
            } else if (!isIncludeVHFBands()) {
                // Remove the VHF bands of UHF stations if we are doing a UHF-only auction
                ((StationInfo) s).setMinChannel(StationPackingUtils.UHFmin);
            }
//            if (city != null && nLinks >= 0) {
//                ignorePredicateFactories.add(new CityAndLinksPredicateFactory(city, nLinks, getStationManager(), getConstraintManager()));
//            }
        }
        toRemove.forEach(stationDB::removeStation);


        // Assign volumes
        log.info("Setting volumes");
        final IVolumeCalculator volumeCalculator = new CSVVolumeCalculator(getVolumeFile());

        final Set<IStationInfo> americanStations = Sets.newHashSet(stationDB.getStations(Nationality.US));
        final Map<Integer, Integer> volumes = volumeCalculator.getVolumes(americanStations);
        for (IStationInfo stationInfo : americanStations) {
            int volume = volumes.get(stationInfo.getId());
            ((StationInfo) stationInfo).setVolume(volume);
        }

        // Set stations as commercial or non-commercial
        final CSVCommercial commercialReader = new CSVCommercial(getCommercialFile());
        final Map<Integer, Boolean> commercialStatus = commercialReader.getCommercialStatus(americanStations);
        for (IStationInfo stationInfo : americanStations) {
            boolean commercial = commercialStatus.get(stationInfo.getId());
            ((StationInfo) stationInfo).setCommercial(commercial);
        }
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
                return new OpeningOffPriceHigherThanPrivateValue(prices);
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
    public static class CityAndLinks {

        @NonNull
        private final String city;
        private final int links;
        private final IStationDB.IModifiableStationDB stationDB;
        private final IConstraintManager constraintManager;

        public void function() {
            // Step 1: Construct the interference graph based on stations in the DB and their domains
            final Map<Station, Set<Integer>> domains = stationDB.getStations()
                    .stream()
                    .collect(Collectors.toMap(IStationInfo::toSATFCStation, IStationInfo::getDomain));

            final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, constraintManager);

            final Set<Station> cityStations = domains.keySet().stream()
                    .filter(s -> stationDB.getStationById(s.getID()).getCity().equals(city))
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

            log.info("Found {} stations within {} links of {}. Removing all other stations", output.size(), links, city);
            final Set<IStationInfo> toRemove = new HashSet<>();
            for (IStationInfo s : stationDB.getStations()) {
                if (!output.contains(s.toSATFCStation())) {
                    toRemove.add(s);
                }
            }
            for (IStationInfo s : toRemove) {
                stationDB.removeStation(s.getId());
            }
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

    public static class CSVCommercial {

        final ImmutableMap<Integer, Boolean> commerical;

        public CSVCommercial(String commercialFile) {
            log.info("Reading commercial status from {}", commercialFile);
            final ImmutableMap.Builder<Integer, Boolean> commericalBuilder = ImmutableMap.builder();
            final Iterable<CSVRecord> commercialRecords = SimulatorUtils.readCSV(commercialFile);
            for (CSVRecord record : commercialRecords) {
                int id = Integer.parseInt(record.get("FacID"));
                boolean isCommerical = Boolean.parseBoolean(record.get("Commercial"));
                commericalBuilder.put(id, isCommerical);
            }
            commerical = commericalBuilder.build();
            log.info("Finished reading commercial status");
        }

        public Map<Integer, Boolean> getCommercialStatus(Set<IStationInfo> stations) {
            return commerical;
        }


    }

    @RequiredArgsConstructor
    public static class NormalizingVolumeDecorator implements IVolumeCalculator {

        private final IVolumeCalculator decorated;

        @Override
        public Map<Integer, Integer> getVolumes(Set<IStationInfo> stations) {
            final Map<Integer, Integer> volumes = decorated.getVolumes(stations);
            final Map<Integer, Integer> normalized = new HashMap<>();

            double max = volumes.values().stream().mapToDouble(x -> x).max().getAsDouble();
            for (Map.Entry<Integer, Integer> entry : volumes.entrySet()) {
                normalized.put(entry.getKey(), (int) Math.round((entry.getValue() / max) * 1e6));
            }
            return normalized;
        }
    }

    public Map<Integer, Integer> getStartingAssignment() {
        final String sFile = getStartingAssignmentFile();
        final Map<Integer, Integer> assignment = new HashMap<>();
        if (sFile != null) {
            for (CSVRecord record : SimulatorUtils.readCSV(sFile)) {
                final int facID = Integer.parseInt(record.get("FacID"));
                final int chan = Integer.parseInt(record.get("Ch"));
                assignment.put(facID, chan);
            }
        }
        return assignment;
    }

}