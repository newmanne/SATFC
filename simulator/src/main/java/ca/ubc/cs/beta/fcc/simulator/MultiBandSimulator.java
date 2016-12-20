package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.bidprocessing.IStationOrderer;
import ca.ubc.cs.beta.fcc.simulator.bidprocessing.StationOrdererImpl;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IProblemMaker;
import ca.ubc.cs.beta.fcc.simulator.ladder.IModifiableLadder;
import ca.ubc.cs.beta.fcc.simulator.parameters.LadderAuctionParameters;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameter;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.prices.IPricesFactory;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.ProblemType;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.fcc.simulator.state.LadderAuctionState;
import ca.ubc.cs.beta.fcc.simulator.state.RoundTracker;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.stepreductions.StepReductionCoefficientCalculator;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.ISimulatorUnconstrainedChecker;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.fcc.simulator.vacancy.IVacancyCalculator;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import humanize.Humanize;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Created by newmanne on 2016-08-02.
 */
@Slf4j
public class MultiBandSimulator {

    private final IVacancyCalculator vacancyCalculator;
    private final IProblemMaker problemMaker;
    private final IFeasibilitySolver solver;

    private final StepReductionCoefficientCalculator stepReductionCoefficientCalculator;

    private final IStationOrderer stationOrderer;
    private final IPricesFactory pricesFactory;
    private final RoundTracker roundTracker;

    private final ISimulatorUnconstrainedChecker unconstrainedChecker;

    private final LadderAuctionParameters parameters;
    private final IStationManager stationManager;
    private final IConstraintManager constraintManager;

    public MultiBandSimulator(MultiBandSimulatorParameter parameters) {
        this.parameters = parameters.getParameters();
        this.problemMaker = parameters.getProblemMaker();
        this.pricesFactory = parameters.getPricesFactory();
        this.solver = parameters.getSolver();
        this.vacancyCalculator = parameters.getVacancyCalculator();
        this.unconstrainedChecker = parameters.getUnconstrainedChecker();
        this.stepReductionCoefficientCalculator = new StepReductionCoefficientCalculator(this.parameters.getOpeningBenchmarkPrices());
        this.stationOrderer = new StationOrdererImpl();
        this.stationManager = parameters.getStationManager();
        this.constraintManager = parameters.getConstraintManager();
        this.roundTracker = parameters.getRoundTracker();
        // TODO: calculate the interference compononent manually? (i.e. manual volume calculation)
    }

    public LadderAuctionState executeRound(LadderAuctionState previousState) {
        roundTracker.incrementRound();
        log.info("Starting round {}", roundTracker.getRound());

        final Map<IStationInfo, Double> stationPrices = new HashMap<>(previousState.getPrices());

        final double oldBaseClockPrice = previousState.getBaseClockPrice();

        final IPrices oldBenchmarkPrices = previousState.getBenchmarkPrices();
        final IPrices newBenchmarkPrices = pricesFactory.create();
        final IPrices actualPrices = pricesFactory.create();

        // TODO: Why not make these fields? Seems like you just reuse them anyways and could make method calls cleaner
        final IModifiableLadder ladder = previousState.getLadder();
        final ParticipationRecord participation = previousState.getParticipation();

        Preconditions.checkState(previousState.getAssignment().equals(ladder.getPreviousAssignment()), "Ladder and previous state do not match on assignments", previousState.getAssignment(), ladder.getPreviousAssignment());
        Preconditions.checkState(StationPackingUtils.weakVerify(stationManager, constraintManager, previousState.getAssignment()), "Round started on an invalid assignment!", previousState.getAssignment());

        log.info("There are {} bidding stations remaining (HB={}), {} provisional winners, and {} frozen stations that are not provisional winners", participation.getMatching(Participation.BIDDING).size(), participation.getMatching(Participation.BIDDING).stream().collect(Collectors.groupingBy(IStationInfo::getHomeBand, Collectors.counting())), participation.getMatching(Participation.FROZEN_PROVISIONALLY_WINNING).size(), participation.getMatching(Participation.FROZEN_CURRENTLY_INFEASIBLE).size());

        log.info("Computing vacancies...");
        final ImmutableTable<IStationInfo, Band, Double> vacancies = vacancyCalculator.computeVacancies(participation.getMatching(Participation.ACTIVE), ladder, previousState.getBenchmarkPrices());

        log.info("Calculating reduction coefficients...");
        final ImmutableTable<IStationInfo, Band, Double> reductionCoefficients = stepReductionCoefficientCalculator.computeStepReductionCoefficients(vacancies, ladder);

        log.info("Calculating new benchmark prices...");
        // Either 5% of previous value or 1% of starting value
        final double decrement = Math.max(parameters.getR1() * oldBaseClockPrice, parameters.getR2() * parameters.getOpeningBenchmarkPrices().get(Band.OFF));
        // This is the benchmark price for a UHF station to go off air
        final double newBaseClockPrice = oldBaseClockPrice - decrement;
        log.info("Base clock moved from {} to {}. Decrement this round is {}", oldBaseClockPrice, newBaseClockPrice, decrement);

        for (IStationInfo station : participation.getMatching(Participation.ACTIVE)) {
            for (Band band : ladder.getBands()) {
                // If this station were a "comparable" UHF station, the prices for all of the moves...
                final double benchmarkValue = oldBenchmarkPrices.getPrice(station, band) - reductionCoefficients.get(station, band) * decrement;
                newBenchmarkPrices.setPrice(station, band, benchmarkValue);
            }
            for (Band band : ladder.getPossibleMoves(station)) {
                actualPrices.setPrice(station, band, SimulatorUtils.benchmarkToActualPrice(station, band, newBenchmarkPrices.getOffers(station)));
            }
        }

        log.info("Collecting bids");
        // Collect bids from bidding stations
        final Set<IStationInfo> biddingStations = participation.getMatching(Participation.BIDDING);
        final Map<IStationInfo, Bid> stationToBid = new HashMap<>();
        for (IStationInfo station : biddingStations) {
            log.debug("Asking station {} to submit a bid", station);
            final Map<Band, Double> offers = actualPrices.getOffers(station);
            log.debug("Offers are {}:", prettyPrintOffers(offers));
            Preconditions.checkState(offers.get(station.getHomeBand()) == 0, "Station %s is being offered money %s to go to its home band!", station, offers.get(station.getHomeBand()));
            final Bid bid = station.queryPreferredBand(offers, ladder.getStationBand(station));
            validateBid(ladder, station, bid);
            stationToBid.put(station, bid);
            log.debug("Bid: {}", bid);
        }

        log.info("Processing bids");
        final ImmutableList<IStationInfo> stationsToQueryOrdering = stationOrderer.getQueryOrder(participation.getMatching(Participation.BIDDING), actualPrices, ladder, previousState.getPrices());
        final List<IStationInfo> stationsToQuery = new ArrayList<>(stationsToQueryOrdering);
        boolean finished = false;
        while (!finished) {
            finished = true;
            for (int i = 0; i < stationsToQuery.size(); i++) {
                // Find the first station proved to be feasible in its pre-auction band
                final IStationInfo station = stationsToQuery.get(i);
                final Band homeBand = station.getHomeBand();
                final Band currentBand = ladder.getStationBand(station);
                log.debug("Checking if {}, currently on {}, is feasible on its home band", station, currentBand, homeBand);
                final SimulatorResult homeBandFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, homeBand, ProblemType.BID_PROCESSING_HOME_BAND_FEASIBLE));
                final boolean isFeasibleInHomeBand = SimulatorUtils.isFeasible(homeBandFeasibility);
                log.debug("{}", homeBandFeasibility.getSATFCResult().getResult());
                if (isFeasibleInHomeBand) {
                    finished = false;
                    // Retrieve the bid
                    final Bid bid = stationToBid.get(station);
                    log.debug("Processing {} bid of {}", station, bid);
                    boolean resortToFallbackBid = false;
                    SimulatorResult moveFeasibility = null;
                    if (!Bid.isSafe(bid.getPreferredOption(), currentBand, station.getHomeBand())) {
                        log.debug("Bid to move bands (without dropping out) - Need to test move feasibility");
                        moveFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, bid.getPreferredOption(), ProblemType.BID_PROCESSING_MOVE_FEASIBLE));
                        resortToFallbackBid = !SimulatorUtils.isFeasible(moveFeasibility);
                    }
                    if (resortToFallbackBid) {
                        log.debug("Not feasible in preferred option. Using fallback option");
                    }
                    final Band moveBand = resortToFallbackBid ? bid.getFallbackOption() : bid.getPreferredOption();
                    if (moveBand.equals(station.getHomeBand())) {
                        log.info("Station {} rejecting offers of {} and moving to exit (value in HB {})", station, prettyPrintOffers(actualPrices.getOffers(station)), Humanize.spellBigNumber(station.getValue()));
                        exitStation(station, Participation.EXITED_VOLUNTARILY, homeBandFeasibility.getSATFCResult().getWitnessAssignment(), participation, ladder, stationPrices);
                    } else {
                        // If an actual move is taking place
                        if (!ladder.getStationBand(station).equals(moveBand)) {
                            Preconditions.checkNotNull(moveFeasibility);
                            ladder.moveStation(station, moveBand, moveFeasibility.getSATFCResult().getWitnessAssignment());
                        }
                        stationPrices.put(station, actualPrices.getPrice(station, moveBand));
                    }
                    stationsToQuery.remove(i);
                    break; // start a new processing loop
                }
            }
        }

        // BID STATUS UPDATING
        log.info("Bid status updating: checking feasibility of stations in home bands");
        // For every active station, check whether the station is feasible in its pre-auction band
        final Map<IStationInfo, SATFCResult> stationToFeasibleInHomeBand = new ConcurrentHashMap<>();
        for (IStationInfo station : stationsToQuery) {
            // If you are still in this queue, you are frozen
            stationToFeasibleInHomeBand.put(station, new SATFCResult(SATResult.UNSAT, 0., 0., ImmutableMap.of()));
        }
        for (final IStationInfo stationInfo : participation.getActiveStations()) {
            if (!stationToFeasibleInHomeBand.containsKey(stationInfo)) {
                solver.getFeasibility(problemMaker.makeProblem(stationInfo, stationInfo.getHomeBand(), ProblemType.BID_STATUS_UPDATING_HOME_BAND_FEASIBLE), new SATFCCallback() {
                    @Override
                    public void onSuccess(SimulatorProblem problem, SimulatorResult result) {
                        stationToFeasibleInHomeBand.put(stationInfo, result.getSATFCResult());
                    }
                });
            }
        }
        solver.waitForAllSubmitted();

        log.info("Checking for provisional winners");
        final Map<Band, List<Entry<IStationInfo, SATFCResult>>> bandListMap = stationToFeasibleInHomeBand.entrySet().stream().collect(Collectors.groupingBy(entry -> entry.getKey().getHomeBand()));
        // Do this in descending band order because this will catch the most provisional winners the earliest. E.g. flagging a UHF station as PW means it participates in VHF problems.
        // We can solve each "home band" in parallel
        for (final Band band : bandListMap.keySet().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
            final List<Entry<IStationInfo, SATFCResult>> bandList = bandListMap.get(band);
            for (Entry<IStationInfo, SATFCResult> entry : bandList) {
                final IStationInfo station = entry.getKey();
                final boolean feasibleInHomeBand = SimulatorUtils.isFeasible(entry.getValue());
                final Participation bidStatus = participation.getParticipation(station);
                if (!feasibleInHomeBand && !bidStatus.equals(Participation.FROZEN_PROVISIONALLY_WINNING)) {
                    if (station.getHomeBand().equals(Band.UHF)) {
                        makeProvisionalWinner(participation, station, stationPrices.get(station));
                    } else {
                        // Need to do a provisional winner check
                        // Provisionally winning if cannot assign s, all exited in s's home band, and all provisionally winning with home bands above currently assigned to s's home band
                        final Set<IStationInfo> provisionalWinnerProblemStationSet = Sets.newHashSet(station);
                        provisionalWinnerProblemStationSet.addAll(
                                participation.getMatching(Participation.INACTIVE).stream()
                                        .filter(s -> ladder.getStationBand(s).equals(station.getHomeBand()))
                                        .collect(Collectors.toSet())
                        );
                        solver.getFeasibility(problemMaker.makeProblem(provisionalWinnerProblemStationSet, station.getHomeBand(), ProblemType.PROVISIONAL_WINNER_CHECK, station), new SATFCCallback() {
                            @Override
                            public void onSuccess(SimulatorProblem problem, SimulatorResult result) {
                                if (!SimulatorUtils.isFeasible(result)) {
                                    makeProvisionalWinner(participation, station, stationPrices.get(station));
                                }
                            }
                        });
                    }
                }
            }
            solver.waitForAllSubmitted();
        }

        log.info("Checking for unconstrained stations");
        for (final Band band : bandListMap.keySet().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
            final List<Entry<IStationInfo, SATFCResult>> bandList = bandListMap.get(band);
            for (Entry<IStationInfo, SATFCResult> entry : bandList) {
                final IStationInfo station = entry.getKey();
                boolean feasibleInHomeBand = SimulatorUtils.isFeasible(entry.getValue());
                if (feasibleInHomeBand) {
                    // for every active station feasible in home band check if it can NEVER become infeasible (not needed)
                    // note we can't use the previous assignment from the feasiblility result eariler incase more than one station exits in the same round, but we are guarenteed to be able to greedily add them to the current assignment
                    if (unconstrainedChecker.isUnconstrained(station, ladder)) {
                        final SimulatorResult feasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, station.getHomeBand(), ProblemType.NOT_NEEDED_UPDATE));
                        Preconditions.checkState(SimulatorUtils.isFeasible(feasibility), "NOT NEEDED station couldn't exit feasibly!");
                        exitStation(station, Participation.EXITED_NOT_NEEDED, feasibility.getSATFCResult().getWitnessAssignment(), participation, ladder, stationPrices);
                    }
                }
                if (participation.isActive(station)) {
                    participation.setParticipation(station, feasibleInHomeBand ? Participation.BIDDING : Participation.FROZEN_CURRENTLY_INFEASIBLE);
                }
            }
        }

        log.info("Round {} complete", roundTracker.getRound());

        return LadderAuctionState.builder()
                .benchmarkPrices(newBenchmarkPrices)
                .participation(participation)
                .round(roundTracker.getRound())
                .assignment(ladder.getPreviousAssignment())
                .ladder(ladder)
                .prices(stationPrices)
                .baseClockPrice(newBaseClockPrice)
                .vacancies(vacancies)
                .reductionCoefficients(reductionCoefficients)
                .offers(actualPrices)
                .bidProcessingOrder(stationsToQueryOrdering)
                .build();
    }

    private void exitStation(IStationInfo station, Participation exitStatus, Map<Integer, Integer> newAssignment, ParticipationRecord participation, IModifiableLadder ladder, Map<IStationInfo, Double> stationPrices) {
        Preconditions.checkState(Participation.EXITED.contains(exitStatus), "Must be an exit Participation");
        log.info("Station {} (currently on band {}) is exiting, {}", station, ladder.getStationBand(station), exitStatus);
        participation.setParticipation(station, exitStatus);
        ladder.moveStation(station, station.getHomeBand(), newAssignment);
        stationPrices.put(station, 0.0);
    }

    private void makeProvisionalWinner(ParticipationRecord participation, IStationInfo station, double price) {
        participation.setParticipation(station, Participation.FROZEN_PROVISIONALLY_WINNING);
        log.info("Station {}, with a value of {}, is now a provisional winner with a price of {}", station, Humanize.spellBigNumber(station.getValue()), Humanize.spellBigNumber(price));
    }

    // Just some sanity checks on bids
    private void validateBid(IModifiableLadder ladder, IStationInfo station, Bid bid) {
        // If the preferred option is neither its currently held option nor to drop out of the auction
        if (!Bid.isSafe(bid.getPreferredOption(), ladder.getStationBand(station), station.getHomeBand())) {
            Preconditions.checkNotNull(bid.getFallbackOption(), "Fallback option was required, but not specified!");
            Preconditions.checkState(bid.getFallbackOption().equals(ladder.getStationBand(station)) || bid.getFallbackOption().equals(station.getHomeBand()), "Must either fallback to currently held option or exit");
        }
    }

    public Map<Band, String> prettyPrintOffers(Map<Band, Double> offers) {
        return offers.entrySet().stream().collect(Collectors.toMap(Entry::getKey, entry -> Humanize.spellBigNumber(entry.getValue())));
    }

}