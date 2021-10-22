package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.bidprocessing.IStationOrderer;
import ca.ubc.cs.beta.fcc.simulator.bidprocessing.StationOrdererImpl;
import ca.ubc.cs.beta.fcc.simulator.catchup.CatchupPoint;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IProblemMaker;
import ca.ubc.cs.beta.fcc.simulator.ladder.IModifiableLadder;
import ca.ubc.cs.beta.fcc.simulator.parameters.LadderAuctionParameters;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameter;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.prices.IPricesFactory;
import ca.ubc.cs.beta.fcc.simulator.solver.DistributedFeasibilitySolver;
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
import ca.ubc.cs.beta.fcc.simulator.valuations.PopValueModel2;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.google.common.eventbus.EventBus;
import com.google.common.math.DoubleMath;
import humanize.Humanize;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.MathUtils;
import org.apache.commons.math3.util.Precision;

import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final SimulatorParameters.BidProcessingAlgorithmParameters bidProcessingAlgorithmParameters;
    private final List<Long> forwardAuctionAmounts;
    private final boolean earlyStopping;
    private final EventBus eventBus;
    private final boolean lockVHFUntilBase;

    private final SATFCResult FAKE_UNSAT_RESULT = new SATFCResult(SATResult.UNSAT, 0., 0., ImmutableMap.of());
    private final MultiBandSimulatorParameters allParameters;
    private final Map<IStationInfo, Long> fccPrices;

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
        this.bidProcessingAlgorithmParameters = parameters.getBidProcessingAlgorithmParameters();
        this.forwardAuctionAmounts = parameters.getForwardAuctionAmounts();
        this.earlyStopping = parameters.isEarlyStopping();
        this.eventBus = parameters.getEventBus();
        this.lockVHFUntilBase = parameters.isLockVHFUntilBase();
        this.allParameters = parameters.getAllParameters();
        this.fccPrices = parameters.getFccPrices();
    }

    public LadderAuctionState executeRound(LadderAuctionState previousState) {
        roundTracker.incrementRound();
        log.info("Starting stage {} round {}", roundTracker.getStage(), roundTracker.getRound());


        final Map<IStationInfo, Long> stationPrices = new HashMap<>(previousState.getPrices());

        final double oldBaseClockPrice = previousState.getBaseClockPrice();

        final IPrices<Double> oldBenchmarkPrices = previousState.getBenchmarkPrices();
        final IPrices<Double> newBenchmarkPrices = pricesFactory.create();
        final IPrices<Long> actualPrices = pricesFactory.create();

        final Map<IStationInfo, CatchupPoint> catchupPoints = new HashMap<>(previousState.getCatchupPoints());

        final IModifiableLadder ladder = previousState.getLadder();
        final ParticipationRecord participation = previousState.getParticipation();

        final boolean earlyTerminationPossible = earlyStopping && forwardAuctionAmounts.size() >= roundTracker.getStage() && ladder.getAirBands().stream().noneMatch(Band::isVHF);

        Preconditions.checkState(previousState.getAssignment().equals(ladder.getPreviousAssignment()), "Ladder and previous state do not match on assignments", previousState.getAssignment(), ladder.getPreviousAssignment());
        Preconditions.checkState(StationPackingUtils.weakVerify(stationManager, constraintManager, previousState.getAssignment()), "Round started on an invalid assignment!", previousState.getAssignment());

        // Start of a new stage. Stations may have unfrozen, what is the new provisional cost?
        if (roundTracker.getStage() > 1 && roundTracker.getRound() == 1) {
            final long provisionalCost = provisionalCost(stationPrices, participation);
            final long provisionalCostExclude = provisionalCost(stationPrices, participation, true);
            log.info("Provisional clearing cost for this stage is at least {} (FROZEN_PROVISIONALLY_WINNING)", Humanize.spellBigNumber(provisionalCost));
            log.info("Exclude Provisional clearing cost for this stage is at least {} (FROZEN_PROVISIONALLY_WINNING)", Humanize.spellBigNumber(provisionalCostExclude));

        }

        log.info("There are {} voluntarily exited stations (HB={})", participation.getMatching(Participation.EXITED_VOLUNTARILY).size(), participation.getMatching(Participation.EXITED_VOLUNTARILY).stream().collect(Collectors.groupingBy(IStationInfo::getHomeBand, Collectors.counting())));
        log.info("There are {} bidding stations remaining (HB={}), {} provisional winners, and {} frozen currently infeasible stations", participation.getMatching(Participation.BIDDING).size(), participation.getMatching(Participation.BIDDING).stream().collect(Collectors.groupingBy(IStationInfo::getHomeBand, Collectors.counting())), participation.getMatching(Participation.FROZEN_PROVISIONALLY_WINNING).size(), participation.getMatching(Participation.FROZEN_CURRENTLY_INFEASIBLE).size());
        final int pendingCatchup = participation.getMatching(Participation.FROZEN_PENDING_CATCH_UP).size();
        if (pendingCatchup > 0) {
            log.info("There are {} stations frozen pending catchup", pendingCatchup);
        }

        log.info("Calculating new benchmark prices...");
        // Either 5% of previous value or 1% of starting value
        final Function<Double, Double> calcDecrement = r1 -> Math.max(r1 * oldBaseClockPrice, parameters.getR2() * parameters.getOpeningBenchmarkPrices().get(Band.OFF));
        double mutableDecrement = calcDecrement.apply(parameters.getR1());
        double proposedNewBaseClockPrice = oldBaseClockPrice - mutableDecrement;

        // Check for empty rounds
        final OptionalDouble maxCatchupPoint = participation.getMatching(Participation.FROZEN_PENDING_CATCH_UP).stream().mapToDouble(s -> catchupPoints.get(s).getCatchUpPoint()).max();
        if (participation.getMatching(Participation.BIDDING).isEmpty() && maxCatchupPoint.isPresent() && proposedNewBaseClockPrice > maxCatchupPoint.getAsDouble()) {
            // Need to prevent an empty round
            final double r1 = Precision.round((oldBaseClockPrice - maxCatchupPoint.getAsDouble()) / oldBaseClockPrice, 2, BigDecimal.ROUND_CEILING);
            log.info("Preventing an empty round by using R1={} due to base clock being at {} after a normal decrement and our highest uncaught station at {}", r1, proposedNewBaseClockPrice, maxCatchupPoint.getAsDouble());
            mutableDecrement = calcDecrement.apply(r1);
        }

        double decrement = mutableDecrement;

        // This is the benchmark price for a UHF station to go off air
        double tmpNewBaseClockPrice = Math.max(oldBaseClockPrice - decrement, 0);


        // If it's close, just set it to 900 so you don't have any headaches with comparison
        if (roundTracker.getStage() == 1 && DoubleMath.fuzzyEquals(tmpNewBaseClockPrice, SimulatorParameters.FCC_UHF_TO_OFF, 1)) {
            tmpNewBaseClockPrice = SimulatorParameters.FCC_UHF_TO_OFF;
            decrement = oldBaseClockPrice - SimulatorParameters.FCC_UHF_TO_OFF; // smooth out the decrement also
        }

        final double newBaseClockPrice = tmpNewBaseClockPrice;

        final boolean vhfUnlocked = !lockVHFUntilBase || newBaseClockPrice <= SimulatorParameters.FCC_UHF_TO_OFF * 0.95; // Unlock at 855
        if (!vhfUnlocked) {
            log.info("VHF is locked this round");
        }

        log.info("Base clock moved from {} to {}. Decrement this round is {}", oldBaseClockPrice, newBaseClockPrice, decrement);
        eventBus.post(new BaseClockMovedEvent(newBaseClockPrice));

        final ImmutableTable<IStationInfo, Band, Double> reductionCoefficients;
        final ImmutableTable<IStationInfo, Band, Double> vacancies;
        if (vhfUnlocked && ladder.getAirBands().stream().anyMatch(Band::isVHF)) {
            log.info("Computing vacancies...");
            vacancies = vacancyCalculator.computeVacancies(participation.getMatching(Participation.ACTIVE), ladder, previousState.getBenchmarkPrices());

            log.info("Calculating reduction coefficients...");
            reductionCoefficients = stepReductionCoefficientCalculator.computeStepReductionCoefficients(vacancies, ladder);
        } else {
            // No concept of vacancy in a UHF-only auction
            vacancies = ImmutableTable.<IStationInfo, Band, Double>builder().build();
            final ImmutableTable.Builder<IStationInfo, Band, Double> builder = ImmutableTable.builder();
            for (IStationInfo station : participation.getMatching(Participation.ACTIVE)) {
                builder.put(station, Band.OFF, 1.0);
                builder.put(station, Band.UHF, 0.0);
            }
            reductionCoefficients = builder.build();
        }


        final ImmutableMap.Builder<IStationInfo, Double> personalDecrementsBuilder = ImmutableMap.builder();
        for (IStationInfo station : participation.getMatching(Participation.FROZEN_PENDING_CATCH_UP)) {
            // Maintain benchmark prices
            for (Band band : ladder.getBands()) {
                newBenchmarkPrices.setPrice(station, band, oldBenchmarkPrices.getPrice(station, band));
            }
            final CatchupPoint catchupPoint = catchupPoints.get(station);
            if (newBaseClockPrice < catchupPoint.getCatchUpPoint() || newBaseClockPrice == 0) {
                // Station has caught up!
                // If it is feasible in its pre-auction band, it is now bidding
                // Else, it is Frozen, currently infeasible. Note that this MUST mean it is a VHF station or it should have been flagged a provisional winner
                final SimulatorResult homeBandFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, station.getHomeBand(), ProblemType.CATCHUP_FEASIBLE));
                final Participation newStatus;
                if (SimulatorUtils.isFeasible(homeBandFeasibility)) {
                    newStatus = Participation.BIDDING;
                } else {
                    Preconditions.checkState(station.getHomeBand().isVHF(), "Only a VHF station can be Frozen-Currently Infeasible after being a Provisional Winner. Yet station %s is not", station);
                    newStatus = Participation.FROZEN_CURRENTLY_INFEASIBLE;
                }

                // Calculate a personal decrement
                final double personalDecrement = catchupPoint.getBenchmarkPrices().get(Band.OFF) - newBaseClockPrice;
                Preconditions.checkState(personalDecrement - decrement <= 10e-3, "Personal decrement for %s is %s which is not <= overall decrement of %s", personalDecrement, station, decrement);
                personalDecrementsBuilder.put(station, personalDecrement);
                participation.setParticipation(station, newStatus);
                log.info("Station {}, currently on {}, has caught up. Status switched to {}", station, ladder.getStationBand(station), newStatus);
            }
        }
        final ImmutableMap<IStationInfo, Double> personalDecrements = personalDecrementsBuilder.build();

        for (IStationInfo station : participation.getMatching(Sets.difference(Participation.ACTIVE, ImmutableSet.of(Participation.FROZEN_PENDING_CATCH_UP)))) {
            for (Band band : ladder.getBands()) {
                if (!vhfUnlocked && (band.isVHF() || station.getHomeBand().isVHF())) {
                    // Don't change any benchmark prices for VHF bands or VHF stations until the time at which VHF unlocks
                    newBenchmarkPrices.setPrice(station, band, oldBenchmarkPrices.getPrice(station, band));
                    continue;
                }

                // If this station were a "comparable" UHF station, the prices for all of the moves...
                // Use the personal decrement when available (because the station is newly caught up), otherwise use the normal decrement
                final double benchmarkValue = oldBenchmarkPrices.getPrice(station, band) - reductionCoefficients.get(station, band) * personalDecrements.getOrDefault(station, decrement);
                newBenchmarkPrices.setPrice(station, band, Math.max(0, benchmarkValue));
            }
            for (Band band : ladder.getPossibleMoves(station)) {
                if (!vhfUnlocked && band.isVHF()) {
                    continue;
                }
                final long newPrice = SimulatorUtils.benchmarkToActualPrice(station, band, newBenchmarkPrices.getOffers(station));
                if (actualPrices.getOffers(station).keySet().contains(band)) {
                    final long prevPrice = actualPrices.getPrice(station, band);
                    Preconditions.checkState(newPrice <= prevPrice, "Station's price rose! Violates incentive properties");
                }
                actualPrices.setPrice(station, band, newPrice);
            }
        }

        // Persist so they can be used in vacancy calculations. They do not decrease
        participation.getMatching(Participation.FROZEN_PROVISIONALLY_WINNING).forEach(s -> {
            for (Band band : ladder.getBands()) {
                newBenchmarkPrices.setPrice(s, band, oldBenchmarkPrices.getPrice(s, band));
            }
        });

        log.info("Collecting bids");
        // Collect bids from bidding stations
        final Set<IStationInfo> biddingStations = participation.getMatching(Participation.BIDDING);
        if (!vhfUnlocked) {
            biddingStations.removeAll(biddingStations.stream().filter(s -> s.getHomeBand().isVHF()).collect(Collectors.toSet()));
        }

        final Map<IStationInfo, Bid> stationToBid = new HashMap<>();
        for (IStationInfo station : biddingStations) {
            log.debug("Asking station {} to submit a bid", station);
            Map<Band, Long> offers = actualPrices.getOffers(station);
            log.debug("Offers are {}:", prettyPrintOffers(offers));
            Preconditions.checkState(offers.get(station.getHomeBand()) == 0, "Station %s is being offered money %s to go to its home band!", station, offers.get(station.getHomeBand()));

            if (!vhfUnlocked) { // Still in the above region. Remove the VHF options
                offers = new HashMap<>(offers);
                for (Band b : ladder.getAirBands().stream().filter(b -> b.isVHF() && !station.getHomeBand().equals(b)).collect(Collectors.toSet())) {
                    offers.remove(b);
                }
            }

            final Bid bid = station.queryPreferredBand(offers, ladder.getStationBand(station));
            validateBid(ladder, station, bid);
            stationToBid.put(station, bid);
            log.debug("Bid: {}", bid);
        }

        log.info("Processing bids");
        // TODO: You may want to play with the orderer for the first-to-finish algorithm, because in practice without N (stations) number of CPUs, it will matter a TON!
        final ImmutableList<IStationInfo> stationsToQueryOrdering = stationOrderer.getQueryOrder(biddingStations, actualPrices, ladder, previousState.getPrices());
        final List<IStationInfo> stationsToQuery = new ArrayList<>(stationsToQueryOrdering);

        // TODO: Can you move these to their own class or something?
        boolean earlyTermination = false;
        long runningProvisionalCost = provisionalCost(stationPrices, participation, true);
        final Set<IStationInfo> willBeFrozen = new HashSet<>();
        // When using a BP algorithm that doesn't freeze timeouts, track these stations
        final Set<IStationInfo> timeoutStations = new HashSet<>();
        if (bidProcessingAlgorithmParameters.getBidProcessingAlgorithm().equals(SimulatorParameters.BidProcessingAlgorithm.FCC) || bidProcessingAlgorithmParameters.getBidProcessingAlgorithm().equals(SimulatorParameters.BidProcessingAlgorithm.NO_PRICE_DROPS_FOR_TIMEOUTS)) {
            boolean finished = false;
            while (!finished) {
                finished = true;
                for (int i = 0; i < stationsToQuery.size(); i++) {
                    // Find the first station proved to be feasible in its pre-auction band
                    final IStationInfo station = stationsToQuery.get(i);
                    final Band homeBand = station.getHomeBand();
                    final Band currentBand = ladder.getStationBand(station);
                    log.debug("Checking if {}, currently on {}, is feasible on its home band {}", station, currentBand, homeBand);
                    final SimulatorResult homeBandFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, homeBand, ProblemType.BID_PROCESSING_HOME_BAND_FEASIBLE));

                    if (!homeBandFeasibility.getSATFCResult().getResult().isConclusive()) {
                        timeoutStations.add(station);
                    }

                    final boolean isFeasibleInHomeBand = SimulatorUtils.isFeasible(homeBandFeasibility);
                    log.debug("{}", homeBandFeasibility.getSATFCResult().getResult());
                    if (isFeasibleInHomeBand) {
                        finished = false;
                        // Retrieve the bid
                        final Bid bid = stationToBid.get(station);
                        processBid(bid, station, homeBandFeasibility, ladder, stationPrices, actualPrices, participation);
                        stationsToQuery.remove(i);
                        break; // start a new processing loop
                    } else {
                        if (ladder.getAirBands().stream().noneMatch(Band::isVHF)) {
                            if (willBeFrozen.add(station)) {
                                if (homeBandFeasibility.getSATFCResult().getResult().isConclusive() || !bidProcessingAlgorithmParameters.getBidProcessingAlgorithm().equals(SimulatorParameters.BidProcessingAlgorithm.NO_PRICE_DROPS_FOR_TIMEOUTS)) {
                                    log.info("Station {} will become a provisional winner at the end of this round for {}", station, Humanize.spellBigNumber(stationPrices.get(station)));
                                }
                                if (earlyTerminationPossible && vhfUnlocked) { // Only count this if the station is below FCC prices
                                    runningProvisionalCost += stationPrices.get(station);
                                    if (runningProvisionalCost > forwardAuctionAmounts.get(roundTracker.getStage() - 1)) {
                                        log.info("Clearing costs are {} exceeding forward auction costs of {}, terminating the bid processing", runningProvisionalCost, forwardAuctionAmounts.get(roundTracker.getStage() - 1));
                                        earlyTermination = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (bidProcessingAlgorithmParameters.getBidProcessingAlgorithm().equals(SimulatorParameters.BidProcessingAlgorithm.FIRST_TO_FINISH_SINGLE_PROGRAM)) {
            // TODO: This is very much UHF-only for now
            Preconditions.checkState(ladder.getAirBands().stream().noneMatch(Band::isVHF), "First to finish not implemented yet for VHF");

            final double timeLimit = bidProcessingAlgorithmParameters.getRoundTimer();
            double currentCutoff = 1;
            double elapsedTime = 0;
            boolean finished = false;
            while (!finished) {
                final Map<IStationInfo, SimulatorResult> results = new HashMap<>();
                for (final IStationInfo station : stationsToQuery) {
                    final Band homeBand = station.getHomeBand();
                    final Band currentBand = ladder.getStationBand(station);
                    log.debug("Checking if {}, currently on {}, is feasible on its home band, {}", station, currentBand, homeBand);
                    final SimulatorProblem simulatorProblem = problemMaker.makeProblem(station, homeBand, ProblemType.BID_PROCESSING_HOME_BAND_FEASIBLE);
                    simulatorProblem.getSATFCProblem().setCutoff(currentCutoff);
                    final SimulatorResult homeBandFeasibility = solver.getFeasibilityBlocking(simulatorProblem);
                    // TODO: In VHF case this condition might need to be different
                    if (homeBandFeasibility.getSATFCResult().getResult().equals(SATResult.SAT) &&
                            stationToBid.get(station).getPreferredOption().equals(Band.OFF) &&
                            !homeBandFeasibility.isCached()) {
                        // In a UHF-only auction, if a station exits, this will trigger the break. So just find the first.
                        currentCutoff = Math.min(homeBandFeasibility.getSATFCResult().getRuntime(), currentCutoff);
                        // Prevent it from being 0. SATFC seems to have trouble with really small cutoffs (was previously trying 0.01 s and having troubles)
                        currentCutoff = Math.max(currentCutoff, 1);
                        log.debug("Decreasing round cutoff to {}", currentCutoff);
                    }
                    results.put(station, homeBandFeasibility);
                }
                // Sort the results in order of SAT completion time
                final List<Entry<IStationInfo, SimulatorResult>> entryList = results.entrySet().stream()
                        .filter(x -> x.getValue().getSATFCResult().getResult().equals(SATResult.SAT))
                        .sorted(Comparator.comparingDouble(x -> x.getValue().getSATFCResult().getRuntime()))
                        .collect(Collectors.toList());

                for (final Entry<IStationInfo, SimulatorResult> entry : entryList) {
                    final SimulatorResult result = entry.getValue();
                    final IStationInfo station = entry.getKey();
                    final Bid bid = stationToBid.get(station);
                    processBid(bid, station, result, ladder, stationPrices, actualPrices, participation);
                    stationsToQuery.remove(station);
                    // TODO: In the VHF case, you need to do more work here!
                    if (participation.getParticipation(station).equals(Participation.EXITED_VOLUNTARILY)) {
                        // Need to "restart" all checks, so add to the elapsed time
                        elapsedTime += result.getSATFCResult().getRuntime();
                        break;
                    }
                }

                // TODO: Time tracking code is complicated :(
                // If you exit, you force people to restart, so add that time for sure.
                // Otherwise, add the "last" time, either when you empty the queue or run out of time
                // Be careful not to double count the case where the last guy exits

                if (stationsToQuery.isEmpty()) {
                    log.info("Round has completed with all checks successful after {}", elapsedTime);
                    finished = true;
                } else {
                    // double the cutoff
                    // Can't this leave you with a small pocket of useless time? No, because no time "elapses"
                    final double nextCutoff = Math.max(1, Math.min(currentCutoff * 2, timeLimit - elapsedTime));
                    if (nextCutoff <= 0 || nextCutoff <= currentCutoff) {
                        // truly done
                        log.info("Round has expired after the time limit of {}. {} stations remain indeterminate.", timeLimit, results.values().stream().filter(x -> !x.getSATFCResult().getResult().isConclusive()).count());
                        finished = true;
                    } else {
                        log.info("Increasing cutoff for the round from {} to {}", currentCutoff, nextCutoff);
                        currentCutoff = nextCutoff;
                    }
                }
            }
        } else {
            throw new IllegalStateException("Unknown bid processing algorithm " + bidProcessingAlgorithmParameters.getBidProcessingAlgorithm());
        }

        if (!timeoutStations.isEmpty()) {
            log.info("There are {} stations who timed out during bid processing", timeoutStations.size());
            eventBus.post(new BidProcessingFinishedEvent(bidProcessingAlgorithmParameters));
        }

        final Map<IStationInfo, SATFCResult> stationToFeasibleInHomeBand = new ConcurrentHashMap<>();
        // BID STATUS UPDATING
        log.info("Bid status updating: checking feasibility of stations in home bands");
        if (earlyTermination) {
            // The auction is going to early terminate
            // All active stations become "fake" PW together
            for (IStationInfo station : participation.getActiveStations()) {
                stationToFeasibleInHomeBand.put(station, FAKE_UNSAT_RESULT);
            }
        } else {
            // For every active station, check whether the station is feasible in its pre-auction band
            for (IStationInfo station : stationsToQuery) {
                // If you are still in this queue, you are frozen unless the NPDFT alg is used
                if (timeoutStations.contains(station) && bidProcessingAlgorithmParameters.getBidProcessingAlgorithm().equals(SimulatorParameters.BidProcessingAlgorithm.NO_PRICE_DROPS_FOR_TIMEOUTS)) {
                    continue;
                }

                stationToFeasibleInHomeBand.put(station, FAKE_UNSAT_RESULT);
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
        }

        log.info("There are {} stations with timeout results after bid status updating", stationToFeasibleInHomeBand.values().stream().filter(v -> !v.getResult().isConclusive()).count());


        // What if EVERYONE left is a timeout and you are running the no price drop for timeout alg? Then you just want to end the auction
        if (bidProcessingAlgorithmParameters.getBidProcessingAlgorithm().equals(SimulatorParameters.BidProcessingAlgorithm.NO_PRICE_DROPS_FOR_TIMEOUTS)) {
            if (stationToFeasibleInHomeBand.values().stream().allMatch(v -> v.getResult().equals(SATResult.TIMEOUT))) {
                log.info("Only timeouts remain, ending auction");
                for (IStationInfo s : new HashSet<>(stationToFeasibleInHomeBand.keySet())) {
                    stationToFeasibleInHomeBand.put(s, FAKE_UNSAT_RESULT);
                }
            }
        }

        log.info("Checking for provisional winners");
        final Map<Band, List<Entry<IStationInfo, SATFCResult>>> bandListMap = stationToFeasibleInHomeBand.entrySet().stream().collect(Collectors.groupingBy(entry -> entry.getKey().getHomeBand()));
        // Do this in descending band order because this will catch the most provisional winners the earliest. E.g. flagging a UHF station as PW means it participates in VHF problems.
        // We can solve each "home band" in parallel
        for (final Band band : bandListMap.keySet().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
            final List<Entry<IStationInfo, SATFCResult>> bandList = bandListMap.get(band);
            for (Entry<IStationInfo, SATFCResult> entry : bandList) {
                final IStationInfo station = entry.getKey();

                if (!entry.getValue().getResult().isConclusive() && bidProcessingAlgorithmParameters.getBidProcessingAlgorithm().equals(SimulatorParameters.BidProcessingAlgorithm.NO_PRICE_DROPS_FOR_TIMEOUTS)) {
                    // Don't let them become PW
                    continue;
                }

                final boolean feasibleInHomeBand = SimulatorUtils.isFeasible(entry.getValue());
                final Participation bidStatus = participation.getParticipation(station);
                if (!feasibleInHomeBand && !bidStatus.equals(Participation.FROZEN_PROVISIONALLY_WINNING)) {
                    if (station.getHomeBand().equals(Band.UHF)) {
                        makeProvisionalWinner(participation, station, stationPrices.get(station), catchupPoints, newBaseClockPrice, newBenchmarkPrices.getOffers(station));
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
                                    makeProvisionalWinner(participation, station, stationPrices.get(station), catchupPoints, newBaseClockPrice, newBenchmarkPrices.getOffers(station));
                                }
                            }
                        });
                    }
                }
            }
            solver.waitForAllSubmitted();
        }

        if (!earlyTerminationPossible) {
            log.info("Checking for unconstrained stations");
            for (final Band band : bandListMap.keySet().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                final List<Entry<IStationInfo, SATFCResult>> bandList = bandListMap.get(band);
                for (Entry<IStationInfo, SATFCResult> entry : bandList) {
                    final IStationInfo station = entry.getKey();
                    boolean feasibleInHomeBand = SimulatorUtils.isFeasible(entry.getValue());
                    if (feasibleInHomeBand) {
                        // for every active station feasible in home band check if it can NEVER become infeasible (not needed)
                        // note we can't use the previous assignment from the feasiblility result earlier in case more than one station exits in the same round, but we are guarenteed to be able to greedily add them to the current assignment
                        if (unconstrainedChecker.isUnconstrained(station, ladder)) {
                            final SimulatorResult feasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, station.getHomeBand(), ProblemType.NOT_NEEDED_UPDATE));
                            Preconditions.checkState(SimulatorUtils.isFeasible(feasibility), "NOT NEEDED station couldn't exit feasibly! Result %s", feasibility.getSATFCResult().getResult());
                            exitStation(station, Participation.EXITED_NOT_NEEDED, feasibility.getSATFCResult().getWitnessAssignment(), participation, ladder, stationPrices);
                        }
                    }
                    if (participation.isActive(station) && !participation.getParticipation(station).equals(Participation.FROZEN_PENDING_CATCH_UP)) {
                        boolean limbo = !entry.getValue().getResult().isConclusive() && bidProcessingAlgorithmParameters.getBidProcessingAlgorithm().equals(SimulatorParameters.BidProcessingAlgorithm.NO_PRICE_DROPS_FOR_TIMEOUTS);
                        participation.setParticipation(station, feasibleInHomeBand || limbo ? Participation.BIDDING : Participation.FROZEN_CURRENTLY_INFEASIBLE);
                    }
                }
            }
        }

        log.info("Stage {} Round {} complete", roundTracker.getStage(), roundTracker.getRound());

        if (!earlyTermination) {
            final long provisionalCost = provisionalCost(stationPrices, participation);
            final long provisionalCostExclude = provisionalCost(stationPrices, participation, true);
            log.info("Provisional clearing cost for this stage is at least {} (FROZEN_PROVISIONALLY_WINNING)", Humanize.spellBigNumber(provisionalCost));
            log.info("Exclude proivisional clearing cost for this stage is at least {} (FROZEN_PROVISIONALLY_WINNING)", Humanize.spellBigNumber(provisionalCostExclude));
        }

        return LadderAuctionState.builder()
                .benchmarkPrices(newBenchmarkPrices)
                .participation(participation)
                .round(roundTracker.getRound())
                .stage(roundTracker.getStage())
                .assignment(ladder.getPreviousAssignment())
                .ladder(ladder)
                .prices(stationPrices)
                .baseClockPrice(newBaseClockPrice)
                .vacancies(vacancies)
                .reductionCoefficients(reductionCoefficients)
                .offers(actualPrices)
                .bidProcessingOrder(stationsToQueryOrdering)
                .catchupPoints(catchupPoints)
                .earlyStopped(earlyTermination)
                .biddingCompensation((long) participation.getMatching(Participation.BIDDING).stream().mapToDouble(stationPrices::get).sum())
                .provisionallyWinningCompensation((long) participation.getMatching(Participation.FROZEN_PROVISIONALLY_WINNING).stream().mapToDouble(stationPrices::get).sum())
                .pendingCatchupCompensation((long) participation.getMatching(Participation.FROZEN_PENDING_CATCH_UP).stream().mapToDouble(stationPrices::get).sum())
                .currentlyInfeasibleCompensation((long) participation.getMatching(Participation.FROZEN_CURRENTLY_INFEASIBLE).stream().mapToDouble(stationPrices::get).sum())
                .build();
    }

    private long provisionalCost(Map<IStationInfo, Long> stationPrices, ParticipationRecord participation, boolean excludePreFCCStations) {
        return stationPrices.entrySet().stream().filter(e -> participation.getParticipation(e.getKey()).equals(Participation.FROZEN_PROVISIONALLY_WINNING) && (!excludePreFCCStations || e.getValue() <= fccPrices.get(e.getKey()))).mapToLong(Entry::getValue).sum();
    }

    private long provisionalCost(Map<IStationInfo, Long> stationPrices, ParticipationRecord participation) {
        return provisionalCost(stationPrices, participation, false);
    }

        static void exitStation(IStationInfo station, Participation exitStatus, Map<Integer, Integer> newAssignment, ParticipationRecord participation, IModifiableLadder ladder, Map<IStationInfo, Long> stationPrices) {
        Preconditions.checkState(Participation.EXITED.contains(exitStatus), "Must be an exit Participation");
        log.info("Station {} (currently on band {}) is exiting, {}", station, ladder.getStationBand(station), exitStatus);
        participation.setParticipation(station, exitStatus);
        ladder.moveStation(station, station.getHomeBand(), newAssignment);
        stationPrices.put(station, 0L);
    }

    private void makeProvisionalWinner(ParticipationRecord participation, IStationInfo station, long price, Map<IStationInfo, CatchupPoint> catchupPoints, double baseClock, Map<Band, Double> benchmarkPrices) {
        participation.setParticipation(station, Participation.FROZEN_PROVISIONALLY_WINNING);
        log.info("Station {}, with a value of {}, is now a provisional winner with a price of {}", station, Humanize.spellBigNumber(station.getValue()), Humanize.spellBigNumber(price));
        if (catchupPoints.get(station) == null || catchupPoints.get(station).getCatchUpPoint() > baseClock) {
            // If you were previously a provisional winner and don't bid to accept a lower price offer, your price doesn't change
            // q_{s,b} is the LOWEST base clock price a station became provisionally winning in a previous stage
            catchupPoints.put(station, new CatchupPoint(baseClock, benchmarkPrices));
        }
    }

    // Just some sanity checks on bids
    private void validateBid(IModifiableLadder ladder, IStationInfo station, Bid bid) {
        // If the preferred option is neither its currently held option nor to drop out of the auction
        if (!Bid.isSafe(bid.getPreferredOption(), ladder.getStationBand(station), station.getHomeBand())) {
            Preconditions.checkNotNull(bid.getFallbackOption(), "Fallback option was required, but not specified!");
            Preconditions.checkState(bid.getFallbackOption().equals(ladder.getStationBand(station)) || bid.getFallbackOption().equals(station.getHomeBand()), "Must either fallback to currently held option or exit");
        }
    }

    public Map<Band, String> prettyPrintOffers(Map<Band, Long> offers) {
        return offers.entrySet().stream().collect(Collectors.toMap(Entry::getKey, entry -> Humanize.spellBigNumber(entry.getValue())));
    }

    public void processBid(Bid bid, IStationInfo station, SimulatorResult homeBandFeasibility, IModifiableLadder ladder, Map<IStationInfo, Long> stationPrices, IPrices<Long> actualPrices, ParticipationRecord participation) {
        log.debug("Processing {} bid of {}", station, bid);
        boolean resortToFallbackBid = false;
        SimulatorResult moveFeasibility = null;
        if (!Bid.isSafe(bid.getPreferredOption(), ladder.getStationBand(station), station.getHomeBand())) {
            log.debug("Bid to move bands (without dropping out) - Need to test move feasibility");
            moveFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, bid.getPreferredOption(), ProblemType.BID_PROCESSING_MOVE_FEASIBLE));
            resortToFallbackBid = !SimulatorUtils.isFeasible(moveFeasibility);
        }
        if (resortToFallbackBid) {
            log.debug("Not feasible in preferred option. Using fallback option");
        }
        final Band moveBand = resortToFallbackBid ? bid.getFallbackOption() : bid.getPreferredOption();
        if (moveBand.equals(station.getHomeBand())) {
            log.info("{}Station {} rejecting offers of {} and moving to exit (values: {})", resortToFallbackBid ? "(FALLBACK) " : "", station, prettyPrintOffers(actualPrices.getOffers(station)), prettyPrintOffers(station.getValues()));
            exitStation(station, Participation.EXITED_VOLUNTARILY, homeBandFeasibility.getSATFCResult().getWitnessAssignment(), participation, ladder, stationPrices);
        } else {
            // If an actual move is taking place
            if (!ladder.getStationBand(station).equals(moveBand)) {
                Preconditions.checkNotNull(moveFeasibility);
                ladder.moveStation(station, moveBand, moveFeasibility.getSATFCResult().getWitnessAssignment());
            }
            stationPrices.put(station, actualPrices.getPrice(station, moveBand));
        }
    }

}