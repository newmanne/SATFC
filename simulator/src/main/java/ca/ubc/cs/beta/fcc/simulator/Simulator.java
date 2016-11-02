package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.prices.PricesImpl;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.decorator.FeasibilityResultDistributionDecorator;
import ca.ubc.cs.beta.fcc.simulator.solver.decorator.TimeTrackerFeasibilitySolverDecorator;
import ca.ubc.cs.beta.fcc.simulator.state.AuctionState;
import ca.ubc.cs.beta.fcc.simulator.state.IStateSaver;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.time.TimeTracker;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableMap;

public class Simulator {

    private static Logger log;

    public static void main(String[] args) throws Exception {
//        final SimulatorParameters parameters = new SimulatorParameters();
//        JCommanderHelper.parseCheckingForHelpAndVersion(args, parameters);
//        // TODO: probably want to override the default log file name...
//        SATFCFacadeBuilder.initializeLogging(parameters.getFacadeParameters().getLogLevel(), parameters.getFacadeParameters().logFileName);
//        JCommanderHelper.logCallString(args, Simulator.class);
//        log = LoggerFactory.getLogger(Simulator.class);
//
//        parameters.setUp();
//
//
//        log.info("Building solver");
//
//        final TimeTracker timeTracker = new TimeTracker();
//        final FeasibilityResultDistributionDecorator.FeasibilityResultDistribution feasibilityResultDistribution = new FeasibilityResultDistributionDecorator.FeasibilityResultDistribution();
//        IFeasibilitySolver tmp = parameters.createSolver();
//        tmp = new TimeTrackerFeasibilitySolverDecorator(tmp, timeTracker);
//        tmp = new FeasibilityResultDistributionDecorator(tmp, feasibilityResultDistribution);
//        final IFeasibilitySolver solver = tmp;
//
//        final AtomicInteger round = new AtomicInteger();
//        final SimpleSimulatorProblemMaker problemMaker = new SimpleSimulatorProblemMaker(parameters.createProblemSpecGenerator(), round);
//
//        log.info("Reading station info from file");
//        final StationDB stationDB = parameters.getStationDB();
//
//        final IStateSaver stateSaver = parameters.getStateSaver();
//
//        final IPrices prices;
//        final ParticipationRecord participation;
//        Map<Integer, Integer> assignment;
//        if (parameters.isRestore()) {
//            log.info("Restoring from state");
//            final AuctionState auctionState = stateSaver.restoreState(stationDB);
//            prices = auctionState.getBenchmarkPrices();
//            participation = auctionState.getParticipation();
//            round.set(auctionState.getRound() + 1);
//            assignment = auctionState.getAssignment();
//        } else {
//            // Initialize opening benchmarkPrices
//            log.info("Setting opening prices");
//            prices = new PricesImpl();
//            for (final IStationInfo s : stationDB.getStations()) {
//                if (s.getNationality().equals(Nationality.CA)) {
//                    continue;
//                }
//                prices.setPrice(s, Band.UHF, parameters.getScoringRule().score(s, parameters.getBaseClockPrice()));
//            }
//
//            log.info("Figuring out participation");
//            // Figure out participation
//            participation = new ParticipationRecord(stationDB, parameters.getParticipationDecider(prices));
//            round.set(1);
//            final long notParticipatingUS = stationDB.getStations().stream()
//                    .filter(s -> s.getNationality().equals(Nationality.US))
//                    .map(participation::getParticipation)
//                    .filter(p -> p.equals(Participation.EXITED_NOT_PARTICIPATING))
//                    .count();
//            final long totalUS = stationDB.getStations().stream().filter(s -> s.getNationality().equals(Nationality.US)).count();
//            log.info("There are {} non-participating US stations out of {} US stations", notParticipatingUS, totalUS);
//            final Set<IStationInfo> onAirStations = participation.getOnAirStations();
//            log.info("Finding an initial assignment for the {} initially on air stations", onAirStations.size());
//            final SATFCResult initialFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(onAirStations, ImmutableMap.of()));
//            Preconditions.checkState(SimulatorUtils.isFeasible(initialFeasibility), "Initial non-participating stations do not have a feasible assignment! (Result was %s)", initialFeasibility.getResult());
//            log.info("Found an initial assignment for the non-participating stations");
//            assignment = initialFeasibility.getWitnessAssignment();
//        }
//
//        log.info("There are {} / {} stations participating", participation.getActiveStations().size(), stationDB.getStations().size());
//        // Consider stations in reverse order of their values per volume
//        final Comparator<IStationInfo> valuePerVolumeComparator = Comparator.comparingDouble(a -> a.getValues().get(Band.UHF) / a.getVolume());
//        final List<IStationInfo> activeStationsOrdered = Collections.synchronizedList(participation.getActiveStations()
//                .stream()
//                .sorted(valuePerVolumeComparator.reversed())
//                .collect(Collectors.toList()));
//        final Set<IStationInfo> onAirStations = participation.getOnAirStations();
//
//        while (!participation.getActiveStations().isEmpty()) {
//            log.info("Round {}", round);
//            final Map<Participation, Integer> participationCounts = stationDB.getStations()
//                    .stream()
//                    .collect(Collectors.groupingBy(participation::getParticipation))
//                    .entrySet()
//                    .stream()
//                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));
//            log.info("Participation stats: {}", participationCounts);
//
//            final IStationInfo nextToExit = activeStationsOrdered.remove(0);
//            log.info("Considering station {}", nextToExit);
//            final SATFCResult feasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(Sets.union(onAirStations, ImmutableSet.of(nextToExit)), assignment));
//            if (SimulatorUtils.isFeasible(feasibility)) {
//                // Update assignment and participation
//                log.info("Updating assignment and participation to reflect station exiting");
//                assignment = feasibility.getWitnessAssignment();
//                participation.setParticipation(nextToExit, Participation.EXITED_NOT_NEEDED);
//                onAirStations.add(nextToExit);
//
//                // Update benchmarkPrices
//                prices.setPrice(nextToExit, Band.UHF, nextToExit.getValues().get(Band.UHF));
//                // value = volume * baseClock * gamma^n
//                // so value / volume = baseClock * gamma^n same for everyone
//                final double clockGammaN = nextToExit.getValues().get(Band.UHF) / nextToExit.getVolume();
//                log.debug("Clock Gamma N is {}", clockGammaN);
//                for (final IStationInfo q: activeStationsOrdered) {
//                    double prevPrice = prices.getPrice(q, Band.UHF);
//                    double newPrice = q.getVolume() * clockGammaN;
//                    Preconditions.checkState(newPrice <= prevPrice, "Price must be decreasing! %s %s -> %s", q, prevPrice, newPrice);
//                    prices.setPrice(q, Band.UHF, newPrice);
//                    log.trace("Updating price for Station {} from {} to {}", q, prevPrice, newPrice);
//                }
//
//                // Feasibilty checks for remaining stations - can do this in parallel
//                log.info("Considering other stations to see if they froze as a result of the exit");
//                final Set<IStationInfo> newlyFrozen = new HashSet<>();
//                for (final IStationInfo q : activeStationsOrdered) {
//                    solver.getFeasibility(problemMaker.makeProblem(Sets.union(onAirStations, ImmutableSet.of(q)), assignment), (problem, result) -> {
//                        if (!SimulatorUtils.isFeasible(result)) {
//                            newlyFrozen.add(q);
//                            participation.setParticipation(q, Participation.FROZEN_CURRENTLY_INFEASIBLE);
//                            log.info("Station {} is now frozen due to result of {}", q, result.getResult());
//                        }
//                    });
//                }
//                log.info("Waiting for the {} submitted problems to finish", activeStationsOrdered.size());
//                solver.waitForAllSubmitted();
//                activeStationsOrdered.removeAll(newlyFrozen);
//            } else {
//                Preconditions.checkState(round.get() == 1, "This should only happen in the very first round, otherwise the inner loop frozen checks should catch this!");
//                log.info("Station {} is now frozen due to result of {}", nextToExit, feasibility.getResult());
//                participation.setParticipation(nextToExit, Participation.FROZEN_CURRENTLY_INFEASIBLE);
//            }
//
//            log.info("Saving state for round {}", round);
//            final Map<IStationInfo, Double> pricesMap = new HashMap<>();
//            for (IStationInfo s: stationDB.getStations()) {
//                pricesMap.put(s, prices.getPrice(s, Band.UHF));
//            }
//            // TODO: this needs fixing...
////            stateSaver.saveState(stationDB, pricesMap, participation, assignment, round.get(), feasibilityResultDistribution.histogram(), timeTracker, null);
//            log.info("Reporting timing info for round {}", round);
//            timeTracker.report();
//            feasibilityResultDistribution.report();
//            round.incrementAndGet();
//        }
//
//        solver.close();
//        timeTracker.report();
//        log.info("Finished simulation");
    }

    public static class SimpleSimulatorProblemMaker {

        private final ISATFCProblemSpecGenerator generator;
        private final AtomicInteger counter = new AtomicInteger();
        private final AtomicInteger round;

        public SimpleSimulatorProblemMaker(ISATFCProblemSpecGenerator generator, AtomicInteger round) {
            this.generator = generator;
            this.round = round;
        }

        public SimulatorProblemReader.SATFCProblemSpecification makeProblem(Set<IStationInfo> stations, Map<Integer, Integer> previousAssignment) {
            final Map<Integer, Set<Integer>> domains = stations.stream().collect(toImmutableMap(
                    IStationInfo::getId,
                    IStationInfo::getDomain
            ));
            final SimulatorProblemReader.SATFCProblem satfcProblem = new SimulatorProblemReader.SATFCProblem(
                    domains,
                    previousAssignment
            );
            return generator.createProblem(satfcProblem, "Round_"+round.get()+"_Problem_"+counter.incrementAndGet());
        }

    }

    public interface ISATFCProblemSpecGenerator {

        SimulatorProblemReader.SATFCProblemSpecification createProblem(SimulatorProblemReader.SATFCProblem problem, String name);

    }

}