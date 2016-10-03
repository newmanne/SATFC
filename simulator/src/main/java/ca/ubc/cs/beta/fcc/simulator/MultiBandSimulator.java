package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.bidprocessing.IStationOrderer;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IFeasibilityStateHolder;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.ladder.IModifiableLadder;
import ca.ubc.cs.beta.fcc.simulator.parameters.LadderAuctionParameters;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameter;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prevassign.IPreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.prices.IPricesFactory;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.state.LadderAuctionState;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.stepreductions.StepReductionCoefficientCalculator;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.ISimulatorUnconstrainedChecker;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.fcc.simulator.vacancy.IVacancyCalculator;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Created by newmanne on 2016-08-02.
 */
@Slf4j
public class MultiBandSimulator {

    // TODO: save  / load state
    // TODO: how to leverage parallellism / caching
    // TODO: generate the proper constraint graphs for vacancy to use

    private final IVacancyCalculator vacancyCalculator;
    private final IFeasibilityStateHolder problemMaker;
    private final IFeasibilitySolver solver;

    private final StepReductionCoefficientCalculator stepReductionCoefficientCalculator;

    private final IStationOrderer stationOrderer;
    private final IPricesFactory pricesFactory;

    private final IPreviousAssignmentHandler previousAssignmentHandler;

    private final ISimulatorUnconstrainedChecker unconstrainedChecker;

    private final LadderAuctionParameters parameters;

    public MultiBandSimulator(MultiBandSimulatorParameter parameters) {
        this.parameters = parameters.getParameters();
        this.problemMaker = parameters.getProblemMaker();
        this.pricesFactory = parameters.getPricesFactory();
        this.solver = parameters.getSolver();
        this.vacancyCalculator = parameters.getVacancyCalculator();
        this.unconstrainedChecker = parameters.getUnconstrainedChecker();
        stepReductionCoefficientCalculator = new StepReductionCoefficientCalculator(this.parameters.getOpeningBenchmarkPrices());
        this.previousAssignmentHandler = parameters.getPreviousAssignmentHandler();
        // TODO: fixme
        this.stationOrderer = new IStationOrderer() {
            @Override
            public List<IStationInfo> getQueryOrder(Collection<IStationInfo> stations, IPrices prices, ILadder ladder) {
                final ArrayList<IStationInfo> s = new ArrayList<>(stations);
                Collections.shuffle(s);
                return s;
            }
        };
        // TODO: calculate the interference compononent manually? (i.e. manual volume calculation)
    }

    public static double benchmarkToActualPrice(IStationInfo station, Band band, Map<Band, Double> benchmarkPrices) {
        final double benchmarkHome = benchmarkPrices.get(station.getHomeBand());
        final double nonVolumeWeightedActual = max(0, min(benchmarkPrices.get(Band.OFF), benchmarkPrices.get(band) - benchmarkHome));
        // Price offers are rounded down to nearest integer
        return Math.floor(station.getVolume() * nonVolumeWeightedActual);
    }

    public LadderAuctionState executeRound(LadderAuctionState state) {
        final int round = state.getRound() + 1;
        final Map<IStationInfo, Double> stationPrices = new HashMap<>(state.getPrices());

        final IModifiableLadder ladder = state.getLadder();

        final double oldBaseClockPrice = state.getBaseClockPrice();

        final IPrices oldBenchmarkPrices = state.getBenchmarkPrices();
        final IPrices newBechmarkPrices = pricesFactory.create();
        final ParticipationRecord participation = state.getParticipation();
        final IPrices actualPrices = pricesFactory.create();

        previousAssignmentHandler.updatePreviousAssignment(state.getAssignment());

        log.debug("Computing vacancies...");
        final ImmutableTable<IStationInfo, Band, Double> vacancies = vacancyCalculator.computeVacancies(participation.getMatching(Participation.ACTIVE), ladder, previousAssignmentHandler.getPreviousAssignment());

        log.debug("Calculating reduction coefficients...");
        final ImmutableTable<IStationInfo, Band, Double> reductionCoefficients = stepReductionCoefficientCalculator.computeStepReductionCoefficients(vacancies);

        log.debug("Calculating new benchmark prices...");
        // Either 5% of previous value or 1% of starting value
        final double decrement = Math.max(parameters.getR1() * oldBaseClockPrice, parameters.getR2() * parameters.getOpeningBenchmarkPrices().get(Band.OFF));
        log.debug("Decrement this round is {}", decrement);
        final double newBaseClockPrice = oldBaseClockPrice - decrement;

        for (IStationInfo station : participation.getMatching(Participation.ACTIVE)) {
            for (Band band : ladder.getPossibleMoves(station)) {
                // If this station were a "comparable" UHF station, the prices for all of the moves...
                final double benchmarkValue = oldBenchmarkPrices.getPrice(station, band) - reductionCoefficients.get(station, band) * decrement;
                newBechmarkPrices.setPrice(station, band, benchmarkValue);
                actualPrices.setPrice(station, band, benchmarkToActualPrice(station, band, newBechmarkPrices.getPrices(station, ladder.getPossibleMoves(station))));
            }
        }

        // Collect bids from bidding stations
        final Set<IStationInfo> biddingStations = participation.getMatching(Participation.BIDDING);
        final Map<IStationInfo, Bid> stationToBid = new HashMap<>();
        for (IStationInfo station : biddingStations) {
            log.debug("Asking station {} to submit a bid");
            final ImmutableMap<Band, Double> offers = actualPrices.getPrices(station, ladder.getPossibleMoves(station));
            log.debug("Prices are {}:", offers);
            Preconditions.checkState(offers.get(station.getHomeBand()) == 0, "Station %s is being offered money %s to go to its home band!", station, offers.get(station.getHomeBand()));
            final Bid bid = station.queryPreferredBand(offers, ladder.getStationBand(station));
            // If the preferred option is neither its currently held option nor to drop out of the auction
            if (!bid.getPreferredOption().equals(ladder.getStationBand(station)) && !bid.getPreferredOption().equals(station.getHomeBand())) {
                Preconditions.checkNotNull(bid.getFallbackOption(), "Fallback option was required, but not specified!");
                Preconditions.checkState(bid.getFallbackOption().equals(ladder.getStationBand(station)) || bid.getFallbackOption().equals(station.getHomeBand()), "Must either fallback to currently held option or exit");
            }
            stationToBid.put(station, bid);
            log.debug("Bid: {}.", bid);
        }

        // BID PROCESSING
        // TODO: very confused about sort order
        final List<IStationInfo> stationsToQuery = stationOrderer.getQueryOrder(participation.getMatching(Participation.BIDDING), actualPrices, ladder);
        boolean finished = false;
        while (!finished) {
            finished = true;
            for (int i = 0; i < stationsToQuery.size(); i++) {
                // Find the first station proved to be feasible in its pre-auction band
                final IStationInfo station = stationsToQuery.get(i);
                final Band homeBand = station.getHomeBand();
                log.debug("Checking if {} is feasible on its home band of {}", station, homeBand);
                final SATFCResult feasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, station.getHomeBand()));
                final boolean feasible = !SimulatorUtils.isFeasible(feasibility);
                log.debug(" ... {} ({}).", feasible, feasibility.getResult());
                if (feasible) {
                    finished = false;
                    // Retrieve the bid
                    final Bid bid = stationToBid.get(station);
                    log.debug("Station bid {}", bid);
                    boolean fallback = false;
                    SATFCResult moveFeasibility = null;
                    if (!bid.getPreferredOption().equals(ladder.getStationBand(station)) && !bid.getPreferredOption().equals(station.getHomeBand())) {
                        log.debug("Bid to move bands (without dropping out) - Need to test move feasibility");
                        moveFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, bid.getPreferredOption()));
                        fallback = !SimulatorUtils.isFeasible(moveFeasibility);
                    }
                    if (fallback) {
                        log.debug("Not feasible in preferred option. Using fallback option");
                    }
                    final Band moveBand = !fallback ? bid.getPreferredOption() : bid.getFallbackOption();
                    if (moveBand.equals(station.getHomeBand())) {
                        log.info("Bid to drop out");
                        participation.setParticipation(station, Participation.EXITED_VOLUNTARILY);
                        ladder.moveStation(station, station.getHomeBand());
                        stationPrices.put(station, 0.0);
                        previousAssignmentHandler.updatePreviousAssignment(feasibility.getWitnessAssignment());
                    } else {
                        if (!ladder.getStationBand(station).equals(moveBand)) {
                            ladder.moveStation(station, moveBand);
                            Preconditions.checkNotNull(moveFeasibility);
                            previousAssignmentHandler.updatePreviousAssignment(moveFeasibility.getWitnessAssignment());
                        }
                        stationPrices.put(station, actualPrices.getPrice(station, bid.getPreferredOption()));
                    }
                    stationsToQuery.remove(i);
                    break; // start a new processing loop
                }
            }
        }

        // BID STATUS UPDATING
        // For every active station, check whether the station is feasible in its pre-auction band
        final Map<IStationInfo, Boolean> stationToFeasibleInHomeBand = new ConcurrentHashMap<>();
        for (IStationInfo station : stationsToQuery) {
            // If you are still in this queue, you are frozen
            stationToFeasibleInHomeBand.put(station, false);
        }
        for (final IStationInfo stationInfo : participation.getActiveStations()) {
            if (!stationToFeasibleInHomeBand.containsKey(stationInfo)) {
                solver.getFeasibility(problemMaker.makeProblem(stationInfo, stationInfo.getHomeBand()), new SATFCCallback() {
                    @Override
                    public void onSuccess(SimulatorProblemReader.SATFCProblemSpecification problem, SATFCResult result) {
                        stationToFeasibleInHomeBand.put(stationInfo, SimulatorUtils.isFeasible(result));
                    }
                });
            }
        }
        solver.waitForAllSubmitted();

        // Need to check in band order
        final Comparator<IStationInfo> bandOrder = Comparator.comparingInt(s -> s.getHomeBand().ordinal());
        final List<IStationInfo> stationsToCheck = stationToFeasibleInHomeBand.keySet().stream().sorted(bandOrder.reversed()).collect(Collectors.toList());
        for (IStationInfo station : stationsToCheck) {
            boolean feasibleInHomeBand = stationToFeasibleInHomeBand.get(station);
            final Participation bidStatus = participation.getParticipation(station);

            // Not feasible in pre-auction band and was not provisionally winning
            if (!feasibleInHomeBand && !bidStatus.equals(Participation.FROZEN_PROVISIONALLY_WINNING)) {
                // Is it provisionally winning?
                if (station.getHomeBand().equals(Band.UHF)) {
                    participation.setParticipation(station, Participation.FROZEN_PROVISIONALLY_WINNING);
                } else {
                    // Provisionally winning if cannot assign s, all exited in s's home band, and all provisionally winning with home bands above currently assigned to s's home band
                    final Set<IStationInfo> set = Sets.newHashSet(station);
                    // All exited homed stations
                    set.addAll(participation.getMatching(Participation.EXITED).stream()
                            .filter(s -> s.getHomeBand().equals(station.getHomeBand()))
                            .collect(Collectors.toSet()));
                    // ALl provisionally winning in higher bands in that band
                    set.addAll(participation.getMatching(Participation.FROZEN_PROVISIONALLY_WINNING).stream()
                            .filter(s -> s.getHomeBand().isAbove(station.getHomeBand()) && ladder.getStationBand(s).equals(station.getHomeBand()))
                            .collect(Collectors.toSet()));
                    solver.getFeasibilityBlocking(problemMaker.makeProblem(set, station.getHomeBand()));
                }

                if (participation.getParticipation(station).equals(Participation.FROZEN_PROVISIONALLY_WINNING)) {
                    log.info("Station {} is now a provisional winner with a price of {}", station, stationPrices.get(station));
                }

            } else if (feasibleInHomeBand) {
                // for every active station feasible in home band check if is not needed
                if (unconstrainedChecker.isUnconstrained(station, ladder)) {
                    log.debug("Station {} was determined unconstrained, causing it to exit", station);
                    participation.setParticipation(station, Participation.EXITED_NOT_NEEDED);
                }
            }

            if (!bidStatus.equals(Participation.FROZEN_PROVISIONALLY_WINNING) && !Participation.EXITED.contains(bidStatus)) {
                participation.setParticipation(station, feasibleInHomeBand ? Participation.BIDDING : Participation.FROZEN_CURRENTLY_INFEASIBLE);
            }
        }

        return new LadderAuctionState(
                newBechmarkPrices,
                participation,
                round,
                previousAssignmentHandler.getPreviousAssignment(),
                ladder,
                stationPrices,
                newBaseClockPrice
        );
    }


}