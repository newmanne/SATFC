package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.solver.ISolver;
import ca.ubc.cs.beta.fcc.simulator.station.CSVStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Builder;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Simulator {

    private static Logger log;

    public static void main(String[] args) throws IOException {
        final SimulatorParameters parameters = new SimulatorParameters();
        JCommanderHelper.parseCheckingForHelpAndVersion(args, parameters);
        // TODO: probably want to override the default name...
        SATFCFacadeBuilder.initializeLogging(parameters.getFacadeParameters().getLogLevel(), parameters.getFacadeParameters().logFileName);
        JCommanderHelper.logCallString(args, Simulator.class);
        log = LoggerFactory.getLogger(Simulator.class);

        parameters.setUp();

        log.info("Reading info from file");

        // Read info
        final StationDB stationDB = new CSVStationDB(parameters.getInfoFile());

        log.info("Setting opening prices");

        // Initialize opening prices
        final Prices prices = new OpeningPrices(stationDB, parameters.getBaseClockPrice());

        log.info("Figuring out participation");

        // Figure out participation
        final ParticipationRecord participation = new ParticipationRecord(stationDB, parameters.getParticipationDecider(prices));

        log.info("There are {} / {} stations participating", participation.getActiveStations().size(), stationDB.getStations().size());

        final IStateSaver stateSaver = parameters.getStateSaver();

        log.info("Building solver");
        final ISolver solver = parameters.createSolver();

        // Consider stations in reverse order of their values per volume
        final Comparator<StationInfo> valuePerVolumeComparator = Comparator.comparingDouble(a -> a.getValue() / a.getVolume());
        final List<StationInfo> activeStationsOrdered = Collections.synchronizedList(participation.getActiveStations().stream().sorted(valuePerVolumeComparator.reversed()).collect(Collectors.toList()));
        final Set<StationInfo> onAirStations = participation.getOnAirStations();

        log.info("Finding an initial assignment for the non-participating stations");
        final SATFCResult initialFeasibility = solver.getFeasibilityBlocking(onAirStations, ImmutableMap.of());
        Preconditions.checkState(SimulatorUtils.isFeasible(initialFeasibility), "Initial non-participating stations do not have a feasible assignment! (Result was %s)", initialFeasibility.getResult());
        log.info("Found an initial assignment for the non-participating stations");
        Map<Integer, Integer> assignment = initialFeasibility.getWitnessAssignment();
        while (!participation.getActiveStations().isEmpty()) {
            stateSaver.saveState(stationDB, prices, participation);

            final StationInfo nextToExit = activeStationsOrdered.remove(0);
            log.info("Considering station {}", nextToExit);
            Set<StationInfo> toPack = Sets.union(onAirStations, ImmutableSet.of(nextToExit));
            final SATFCResult feasibility = solver.getFeasibilityBlocking(toPack, assignment);
            log.info("Result of considering station {} was {}", nextToExit, feasibility.getResult());
            if (SimulatorUtils.isFeasible(feasibility)) {
                log.info("Updating assignment and participation to reflect station exiting");
                assignment = feasibility.getWitnessAssignment();
                participation.setParticipation(nextToExit, Participation.EXITED);
                prices.setPrice(nextToExit, nextToExit.getValue());
                // value = volume * baseClock * gamma^n
                // so value / volume = baseClock * gamma^n same for everyone
                final double clockGammaN = nextToExit.getValue() / nextToExit.getVolume();
                log.info("Clock Gamma N is {}", clockGammaN);
                // Update prices for remaining stations - can do this in parallel
                log.info("Considering other stations to see if they froze / update their prices");
                int nProblemsToSubmit = 0;
                final Set<StationInfo> newlyFrozen = new HashSet<>();
                for (final StationInfo q : activeStationsOrdered) {
                    final Set<StationInfo> toPack2 = new HashSet<>(toPack);
                    toPack2.add(q);
                    solver.getFeasibility(toPack2, assignment, (problem, result) -> {
                        if (SimulatorUtils.isFeasible(result)) {
                            double prevPrice = prices.getPrice(q);
                            double newPrice = q.getVolume() * clockGammaN;
                            Preconditions.checkState(newPrice <= prevPrice, "Price must be decreasing! %s %s -> %s", q, prevPrice, newPrice);
                            prices.setPrice(q, newPrice);
                            log.info("Updating price for Station {} from {} to {}", q, prevPrice, newPrice);
                        } else {
                            newlyFrozen.add(q);
                            participation.setParticipation(q, Participation.FROZEN);
                            log.info("Station {} is now frozen", q);
                        }
                    });
                    nProblemsToSubmit += 1;
                }
                log.info("Waiting for the {} submitted problems to finish", nProblemsToSubmit);
                solver.waitForAllSubmitted();
                activeStationsOrdered.removeAll(newlyFrozen);
            } else {
                participation.setParticipation(nextToExit, Participation.FROZEN);
            }
        }

        stateSaver.saveState(stationDB, prices, participation);
        log.info("Finished simulation");
    }

    public interface IStateSaver {

        void saveState(StationDB stationDB, Prices prices, ParticipationRecord participation);

    }

    public static class SaveStateToFile implements IStateSaver {

        String folder;
        int round = 0;

        public SaveStateToFile(String folder) {
            Preconditions.checkNotNull(folder);
            this.folder = folder;
        }

        @Override
        public void saveState(StationDB stationDB, Prices prices, ParticipationRecord participation) {
            final String fileName = folder + File.separator + "state_" + round + ".csv";
            List<List<Object>> records = new ArrayList<>();
            for (StationInfo s : stationDB.getStations()) {
                List<Object> record = new ArrayList<>();
                record.add(s.getId());
                record.add(prices.getPrice(s));
                record.add(participation.getParticipation(s));
                records.add(record);
            }
            SimulatorUtils.toCSV(fileName, Arrays.asList("ID", "Price", "Status"), records);
            round++;
        }
    }

    public interface IProblemGenerator {

        default SATFCProblem createProblem(Set<Integer> stations) {
            return createProblem(stations, ImmutableMap.of());
        }

        SATFCProblem createProblem(Set<Integer> stations, Map<Integer, Integer> previousAssignment);

    }

    public static class ProblemGeneratorImpl implements IProblemGenerator {

        private final Map<Integer, Set<Integer>> domains;

        public ProblemGeneratorImpl(int maxChannel, IStationManager stationManager) {
            domains = new HashMap<>();
            for (Station s : stationManager.getStations()) {
                domains.put(s.getID(), stationManager.getRestrictedDomain(s, maxChannel, true));
            }
        }

        @Override
        public SATFCProblem createProblem(Set<Integer> stations, Map<Integer, Integer> previousAssignment) {
            return SATFCProblem.builder()
                    .domains(Maps.filterKeys(domains, stations::contains))
                    .previousAssignment(previousAssignment)
                    .build();
        }

    }

    @Data
    @Builder
    public static class SATFCProblemSpecification {

        private final SATFCProblem problem;
        private final String stationInfoFolder;
        private double cutoff;
        private long seed;

    }

    @Data
    @Builder
    public static class SATFCProblem {

        private final Map<Integer, Set<Integer>> domains;
        private final Map<Integer, Integer> previousAssignment;

    }

    public interface ISATFCProblemSpecGenerator {

        default SATFCProblemSpecification createProblem(Set<StationInfo> stations) {
            return createProblem(stations, ImmutableMap.of());
        }

        SATFCProblemSpecification createProblem(Set<StationInfo> stations, Map<Integer, Integer> previousAssignment);

    }

    @RequiredArgsConstructor
    public static class ISATFCProblemSpecGeneratorImpl implements ISATFCProblemSpecGenerator {

        private final IProblemGenerator problemGenerator;
        private final String stationInfoFolder;
        private final double cutoff;
        private final long seed;

        @Override
        public SATFCProblemSpecification createProblem(Set<StationInfo> stationInfos, Map<Integer, Integer> previousAssignment) {
            final Set<Integer> stations = SimulatorUtils.toID(stationInfos);
            final SATFCProblem problem = problemGenerator.createProblem(stations, previousAssignment);
            return SATFCProblemSpecification.builder()
                    .problem(problem)
                    .cutoff(cutoff)
                    .seed(seed)
                    .stationInfoFolder(stationInfoFolder)
                    .build();
        }

    }

    public static class OpeningPrices extends PricesImpl {

        public OpeningPrices(StationDB stationDB, double baseClockPrice) {
            super();
            for (final StationInfo s : stationDB.getStations()) {
                final double openPrice = baseClockPrice * s.getVolume();
                setPrice(s, openPrice);
            }
        }

    }

    public interface Prices {

        void setPrice(StationInfo station, Double price);
        double getPrice(StationInfo station);

    }

    public static class PricesImpl implements Prices {

        public PricesImpl() {
            prices = new ConcurrentHashMap<>();
        }

        private final Map<StationInfo, Double> prices;

        public void setPrice(StationInfo station, Double price) {
            prices.put(station, price);
        }

        public double getPrice(StationInfo stationID) {
            return prices.get(stationID);
        }

    }

}
