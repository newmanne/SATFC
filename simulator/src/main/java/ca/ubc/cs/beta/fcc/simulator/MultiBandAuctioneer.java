package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.aeatk.misc.cputime.CPUTime;
import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.fcc.simulator.catchup.CatchupPoint;
import ca.ubc.cs.beta.fcc.simulator.clearingtargetoptimization.ClearingTargetOptimizationMIP;
import ca.ubc.cs.beta.fcc.simulator.clearingtargetoptimization.ClearingTargetOptimizationMIP_2;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IProblemMaker;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.ProblemMakerImpl;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.ladder.IModifiableLadder;
import ca.ubc.cs.beta.fcc.simulator.ladder.LadderEventOnMoveDecorator;
import ca.ubc.cs.beta.fcc.simulator.ladder.SimpleLadder;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameter;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prevassign.IPreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.prevassign.SimplePreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.prices.PricesImpl;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.LocalFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.solver.decorator.*;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.ProblemType;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.SimulatorProblem;
import ca.ubc.cs.beta.fcc.simulator.state.IStateSaver;
import ca.ubc.cs.beta.fcc.simulator.state.LadderAuctionState;
import ca.ubc.cs.beta.fcc.simulator.state.RoundTracker;
import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.ISimulatorUnconstrainedChecker;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.fcc.simulator.vacancy.IVacancyCalculator;
import ca.ubc.cs.beta.fcc.simulator.vacancy.ParallelVacancyCalculator;
import ca.ubc.cs.beta.fcc.vcg.VCGMip;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import humanize.Humanize;
import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import jnr.ffi.annotations.In;
import lombok.Cleanup;
import lombok.Data;
import lombok.Value;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/**
 * Created by newmanne on 2016-09-27.
 */
public class MultiBandAuctioneer {

    private static Logger log;

    public static void main(String[] args) throws Exception {
        final MultiBandSimulatorParameters parameters = new MultiBandSimulatorParameters();
        JCommanderHelper.parseCheckingForHelpAndVersion(args, parameters);
        SATFCFacadeBuilder.initializeLogging(parameters.getFacadeParameters().getLogLevel(), parameters.getFacadeParameters().logFileName, "simulator_logback.groovy");
        JCommanderHelper.logCallString(args, Simulator.class);
        log = LoggerFactory.getLogger(MultiBandAuctioneer.class);

        final Watch simulatorWatch = Watch.constructAutoStartWatch();
        final CPUTime simulatorCPU = new CPUTime();

        parameters.setUp();
        final IStateSaver stateSaver = parameters.getStateSaver();

        final IPreviousAssignmentHandler previousAssignmentHandler = new SimplePreviousAssignmentHandler(parameters.getConstraintManager());

        final IModifiableLadder ladder = new LadderEventOnMoveDecorator(new SimpleLadder(parameters.getAuctionBands(), previousAssignmentHandler), parameters.getEventBus());

        final RoundTracker roundTracker = new RoundTracker();

        final IProblemMaker problemMaker = new ProblemMakerImpl(ladder, parameters.createProblemSpecGenerator(), roundTracker);

        log.info("Reading station info from file");
        final IStationDB.IModifiableStationDB stationDB = parameters.getStationDB();

        adjustCT(parameters.getMaxChannel(), stationDB, parameters.getEventBus(), ladder, parameters.getConstraintManager());
        if (parameters.getCity() != null) {
            new SimulatorParameters.CityAndLinks(parameters.getCity(), parameters.getNLinks(), stationDB, parameters.getConstraintManager()).function();
        }
        if (parameters.getStationsToUseFile() != null) {
            Iterable<CSVRecord> csvRecords = SimulatorUtils.readCSV(parameters.getStationsToUseFile());
            for (CSVRecord record : csvRecords) {
                stationDB.removeStation(Integer.parseInt(record.get("FacID")));
            }
        }

        final Map<IStationInfo, Long> fccPrices = new HashMap<>();
        for (final IStationInfo s : stationDB.getStations()) {
            if (s.getNationality().equals(Nationality.CA) || s.getHomeBand().isVHF()) {
                continue;
            }
            fccPrices.put(s, (long) (s.getVolume() * SimulatorParameters.FCC_UHF_TO_OFF));
        }


        ParticipationRecord participationTmp;
        IPrices<Double> benchmarkPrices;
        IPrices<Long> actualPrices;
        double newBaseClockPrice = parameters.getUHFToOff();
        while (true) {
            // Initialize opening benchmarkPrices
            log.info("Setting opening prices");
            final OpeningPrices setOpeningPrices = setOpeningPrices(parameters, newBaseClockPrice);
            actualPrices = setOpeningPrices.getActualPrices();
            benchmarkPrices = setOpeningPrices.getBenchmarkPrices();

            log.info("Figuring out participation");
            // Figure out participation
            participationTmp = new ParticipationRecord(stationDB, parameters.getParticipationDecider(actualPrices));

            if (parameters.getRaiseClockToFullParticipation()) {
                long uhfCount = participationTmp.getOnAirStations().stream().filter(s -> s.getNationality().equals(Nationality.US) && s.getHomeBand().equals(Band.UHF)).count();
                if (uhfCount > 0) {
                    newBaseClockPrice = newBaseClockPrice * (1 + (parameters.getR1() / (1 - parameters.getR1())));
                    log.info("Raising base clock price to {} get full UHF participation. {} UHF stations not participating,.", newBaseClockPrice, uhfCount);
                } else {
                    log.info("Full US participation achieved!");
                    break;
                }
            } else {
                break;
            }
        }
        final ParticipationRecord participation = participationTmp;

        // This dumps every participating station into OFF. This is fine (provided that the participation decider takes into account that the original OFF offer gives a positive utility), but is an oversimplification due to not simulating the initial clearing target optimization
        for (final IStationInfo s : stationDB.getStations()) {
            ladder.addStation(s, Participation.EXITED.contains(participation.getParticipation(s)) ? s.getHomeBand() : Band.OFF);
        }


        final Set<IStationInfo> nonParticipatingUSStations = stationDB.getStations(Nationality.US).stream()
                .filter(s -> participation.getParticipation(s).equals(Participation.EXITED_NOT_PARTICIPATING)).collect(toSet());


        final Map<Band, Long> participationCounts = stationDB.getStations(Nationality.US).stream().filter(s -> participation.getParticipation(s).equals(Participation.BIDDING)).collect(groupingBy(IStationInfo::getHomeBand, counting()));
        final Map<Band, Long> nonParticipationCounts = stationDB.getStations(Nationality.US).stream().filter(s -> participation.getParticipation(s).equals(Participation.EXITED_NOT_PARTICIPATING)).collect(groupingBy(IStationInfo::getHomeBand, counting()));
        final Map<Band, Long> canadianCounts = stationDB.getStations(Nationality.CA).stream().collect(groupingBy(IStationInfo::getHomeBand, counting()));
        log.info("Participating US station counts: {}", participationCounts);
        log.info("Non-Participating US station counts: {}", nonParticipationCounts);
        log.info("Canadian station counts: {}", canadianCounts);
        final long notParticipatingUS = nonParticipatingUSStations.size();
        final long totalUS = stationDB.getStations().stream().filter(s -> s.getNationality().equals(Nationality.US)).count();
        final long totalCAD = stationDB.getStations().stream().filter(s -> s.getNationality().equals(Nationality.CA)).count();
        log.info("There are {} participating and {} non-participating US stations out of {} US stations. There are {} Canadian stations, making for a total of {} stations.", totalUS - notParticipatingUS, notParticipatingUS, totalUS, totalCAD, stationDB.getStations().size());

        if (notParticipatingUS == totalUS) {
            log.warn("No one is participating in the auction! Ending");
            return;
        }

        final Set<IStationInfo> onAirStations = participation.getOnAirStations();

        final Set<IStationInfo> onAirUHFStations = onAirStations.stream().filter(s -> s.getHomeBand().equals(Band.UHF)).collect(toSet());
        log.info("Finding an initial assignment for the {} initially on-air stations ({} US UHF, {} US VHF)", onAirStations.size(), nonParticipatingUSStations.stream().filter(s -> s.getHomeBand().equals(Band.UHF)).count(), nonParticipatingUSStations.stream().filter(s -> s.getHomeBand().isVHF()).count());
        log.info("Beginning by finding out if any of the {} on-air the UHF stations need to be impaired", onAirStations.stream().filter(s -> s.getHomeBand().equals(Band.UHF)).count());
        final Map<Integer, Integer> assignment = clearingTargetOptimization(stationDB, parameters, onAirUHFStations);
        final Set<IStationInfo> impairingStations = assignment.entrySet().stream().filter(e -> e.getValue() == ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL)
                .map(e -> stationDB.getStationById(e.getKey()))
                .collect(toSet());

        if (!impairingStations.isEmpty()) {
            log.info("There are {} impairing stations ({} Canadian) out of {} UHF non-participating stations. Placing them on a special ({}) channel", impairingStations.size(), impairingStations.stream().filter(s -> s.getNationality().equals(Nationality.CA)).count(), participation.getMatching(Participation.EXITED_NOT_PARTICIPATING).stream().filter(s -> s.getHomeBand().equals(Band.UHF)).count(), ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL);
//            log.info("Impairing value fraction relative to participating stations: {}", (double) impairingStations.stream().mapToLong(IStationInfo::getValue).sum() / participation.getActiveStations().stream().mapToLong(IStationInfo::getValue).sum());
            for (IStationInfo impairingStation : impairingStations) {
                assignment.remove(impairingStation.getId());
                log.info("{} is impairing", impairingStation);
                impairingStation.impair();
            }
            parameters.getEventBus().post(new DomainChangeEvent(ladder, parameters.getConstraintManager()));
        } else {
            log.info("No impairing stations");
        }

        // This is a bit awkward (Should go through the ladder... but oh well)
        previousAssignmentHandler.updatePreviousAssignment(assignment);

        // Get an initial VHF assignment
        // Use a fresh solver here so we don't crash on greedy-only runs
        @Cleanup final IFeasibilitySolver ctSolver = new LocalFeasibilitySolver(new SATFCFacadeBuilder().build());
        final SimulatorResult hvhfSolution = ctSolver.getFeasibilityBlocking(problemMaker.makeProblem(ladder.getBandStations(Band.HVHF), Band.HVHF, ProblemType.INITIAL_PLACEMENT, null));
        previousAssignmentHandler.updatePreviousAssignment(hvhfSolution.getSATFCResult().getWitnessAssignment());
        final SimulatorResult lvhfSolution = ctSolver.getFeasibilityBlocking(problemMaker.makeProblem(ladder.getBandStations(Band.LVHF), Band.LVHF, ProblemType.INITIAL_PLACEMENT, null));
        previousAssignmentHandler.updatePreviousAssignment(lvhfSolution.getSATFCResult().getWitnessAssignment());

        // TODO: I doubt this works for multiple stages
        final ProblemSaverDecorator.ProblemSaverInfo problemSaverInfo = ProblemSaverDecorator.ProblemSaverInfo.builder()
                .interference(parameters.getConstraintSet())
                .maxChannel(parameters.getMaxChannel())
                .build();

        log.info("Building solver");
        IFeasibilitySolver tmp = new VoidFeasibilitySolver();

        if (!parameters.isGreedyOnly()) {
            tmp = new SequentialSolverDecorator(tmp, parameters.createSolver());
        }

        if (parameters.getSwitchFeasibility()) {
            // Once you get below the price, switch the feasibility checker
            final IFeasibilitySolver curSolver = tmp;
            tmp = new IFeasibilitySolver() {

                private boolean isAboveFCCBaseClock = true;

                @Override
                public void getFeasibility(SimulatorProblem problem, SATFCCallback callback) {
                    if (isAboveFCCBaseClock) {
                        ctSolver.getFeasibility(problem, callback);
                    } else {
                        curSolver.getFeasibility(problem, callback);
                    }
                }

                @Override
                public void waitForAllSubmitted() {
                    ctSolver.waitForAllSubmitted();
                    curSolver.waitForAllSubmitted();
                }

                @Override
                public void close() throws Exception {
                    curSolver.close();
                    ctSolver.close();
                }

                @Subscribe
                public void onBaseClockMovedEvent(BaseClockMovedEvent event) {
                    if (isAboveFCCBaseClock && (Math.round(event.getBaseClock())) <= SimulatorParameters.FCC_UHF_TO_OFF * 0.95) {
                        log.info("Base clock has moved below real auction base clock");
                        isAboveFCCBaseClock = false;
                    }
                }

            };
            parameters.getEventBus().register(tmp);
        }

        if (parameters.getBidProcessingAlgorithm().equals(SimulatorParameters.BidProcessingAlgorithm.FIRST_TO_FINISH)) {
            tmp = new DistributedSolverForBidProcessing(tmp, parameters.getBidProcessingAlgorithmParameters().getDistributedFeasibilitySolver());
        }

        if (parameters.isGreedyFirst()) {
            final GreedyFlaggingDecorator greedyFlaggingDecorator = new GreedyFlaggingDecorator(tmp, parameters.getConstraintManager());
            greedyFlaggingDecorator.init(ladder);
            parameters.getEventBus().register(greedyFlaggingDecorator);
            tmp = greedyFlaggingDecorator;
        }

        UHFCachingFeasibilitySolverDecorator uhfCache = null;
        if (parameters.isUHFCache()) {
            uhfCache = new UHFCachingFeasibilitySolverDecorator(tmp, participation, problemMaker, parameters.isLazyUHF(), parameters.isRevisitTimeouts(), ladder, parameters.getConstraintManager());
            uhfCache.init(ladder, parameters.getConstraintManager());
            parameters.getEventBus().register(uhfCache);
            tmp = uhfCache;
        }

        final FeasibilityResultDistributionDecorator.FeasibilityResultDistribution feasibilityResultDistribution = new FeasibilityResultDistributionDecorator.FeasibilityResultDistribution();
        tmp = new FeasibilityResultDistributionDecorator(tmp, feasibilityResultDistribution);
        parameters.getEventBus().register(tmp);

        if (parameters.isStoreProblems()) {
            final ProblemSaverDecorator problemSaverDecorator = new ProblemSaverDecorator(tmp, parameters.getProblemFolder());
            parameters.getEventBus().register(problemSaverDecorator);
            tmp = problemSaverDecorator;
            problemSaverDecorator.writeInfo(problemSaverInfo);
            problemSaverDecorator.writeStartingAssignment(ladder.getPreviousAssignment());
        }

        final TimeTrackerFeasibilitySolverDecorator timeTrackingDecorator = new TimeTrackerFeasibilitySolverDecorator(tmp, simulatorWatch, simulatorCPU);
        tmp = timeTrackingDecorator;
        parameters.getEventBus().register(tmp);

        @Cleanup final IFeasibilitySolver solver = tmp;


        final Map<IStationInfo, Long> initialCompensations = new HashMap<>();
        for (IStationInfo s : ladder.getStations()) {
            if (Participation.NON_ZERO_PRICES.contains(participation.getParticipation(s))) {
                initialCompensations.put(s, actualPrices.getPrice(s, ladder.getStationBand(s)));
            } else {
                initialCompensations.put(s, 0L);
            }
        }

        LadderAuctionState state = LadderAuctionState.builder()
                .benchmarkPrices(benchmarkPrices)
                .participation(participation)
                .round(roundTracker.getRound())
                .assignment(ladder.getPreviousAssignment())
                .ladder(ladder)
                .stage(roundTracker.getStage())
                .prices(initialCompensations)
                .baseClockPrice(newBaseClockPrice)
                .offers(actualPrices)
                .earlyStopped(false)
                .catchupPoints(new HashMap<>())
//                .earlyTerminate(false)
                .build();

        final IVacancyCalculator vacancyCalculator = new ParallelVacancyCalculator(
                participation,
                parameters.getConstraintManager(),
                parameters.getVacFloor(),
                Runtime.getRuntime().availableProcessors()
        );

        final ISimulatorUnconstrainedChecker unconstrainedChecker = parameters.getUnconstrainedChecker(participation, ladder);
        parameters.getEventBus().register(unconstrainedChecker);

        final MultiBandSimulator simulator = new MultiBandSimulator(
                MultiBandSimulatorParameter
                        .builder()
                        .parameters(parameters.getLadderAuctionParameter())
                        .problemMaker(problemMaker)
                        .vacancyCalculator(vacancyCalculator)
                        .solver(solver)
                        .unconstrainedChecker(unconstrainedChecker)
                        .pricesFactory(PricesImpl::new)
                        .constraintManager(parameters.getConstraintManager())
                        .stationManager(parameters.getStationManager())
                        .roundTracker(roundTracker)
                        .bidProcessingAlgorithmParameters(parameters.getBidProcessingAlgorithmParameters())
                        .forwardAuctionAmounts(parameters.getForwardAuctionAmounts())
                        .isEarlyStopping(parameters.isEarlyStopping())
                        .eventBus(parameters.getEventBus())
                        .lockVHFUntilBase(parameters.getLockVHFUntilBase())
                        .allParameters(parameters)
                        .fccPrices(fccPrices)
                        .build()
        );

        log.info("Starting simulation!");


        final List<Integer> stages = SimulatorUtils.CLEARING_TARGETS.stream().filter(c -> c >= parameters.getMaxChannel() && c <= parameters.getMaxChannelFinal() && !parameters.getSkipStages().contains(c)).collect(toList());
        log.info("The auction will proceed for {} stages, with the following max channels: {}", stages.size(), stages);
        if (parameters.getForwardAuctionAmounts().size() > 0) {
            log.info("Forward auction amounts are provided: {}", parameters.getForwardAuctionAmounts().stream().map(Humanize::spellBigNumber).collect(Collectors.toList()));
        }

        boolean auctionSuccessful = true;
        for (int clearingTarget : stages) {
            log.info("Beginning stage {} of the auction", roundTracker.getStage());
            state.setEarlyStopped(false);

            if (parameters.getForwardAuctionAmounts().size() >= roundTracker.getStage() && parameters.isEarlyStopping()) {
                final long forwardAuctionAmount = parameters.getForwardAuctionAmounts().get(roundTracker.getStage() - 1);
                log.info("Early termination for this stage will occur at a provisional cost of {}", Humanize.spellBigNumber(forwardAuctionAmount));
            }

            if (roundTracker.getStage() > 1) {
                adjustCT(clearingTarget, stationDB, parameters.getEventBus(), ladder, parameters.getConstraintManager());

                if (!impairingStations.isEmpty()) {
                    betweenStageClearingTargetOptimization(parameters, previousAssignmentHandler, ladder, stationDB, uhfCache, impairingStations);
                    state.setAssignment(previousAssignmentHandler.getPreviousAssignment());
                }

                log.info("Finding starting base clock price for stage {}", roundTracker.getStage());
                final Set<IStationInfo> provisionalWinners = state.getParticipation().getMatching(Participation.FROZEN_PROVISIONALLY_WINNING);
                final ParticipationRecord endOfStageParticipation = state.getParticipation();

                log.info("Checking whether provisional winners are still provisional winners, or are now frozen pending catchup");
                final Set<IStationInfo> checkUnconstrained = new HashSet<>();
                for (final IStationInfo station : provisionalWinners.stream().sorted(Comparator.comparingInt(s -> -s.getHomeBand().ordinal())).collect(Collectors.toList())) {
                    final SimulatorResult homeBandFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, station.getHomeBand(), ProblemType.BETWEEN_STAGE_HOME_BAND_FEASIBLE));
                    if (SimulatorUtils.isFeasible(homeBandFeasibility)) {
                        endOfStageParticipation.setParticipation(station, Participation.FROZEN_PENDING_CATCH_UP);
                        checkUnconstrained.add(station);
                    } else if (station.getHomeBand().isVHF()) {
                        // If it's a UHF station, and it isn't feasible in it's home band even with the new channels added, then it will remain so for the rest of the stage (and so is still a provisional winner)
                        // If it's a VHF station, need to differentiate between still a PW or F-CI (which would be F-PCU for now)
                        final Set<IStationInfo> provisionalWinnerProblemStationSet = Sets.newHashSet(station);
                        provisionalWinnerProblemStationSet.addAll(
                                participation.getMatching(Participation.INACTIVE).stream()
                                        .filter(s -> ladder.getStationBand(s).equals(station.getHomeBand()))
                                        .collect(toSet())
                        );
                        final SimulatorResult result = solver.getFeasibilityBlocking(problemMaker.makeProblem(provisionalWinnerProblemStationSet, station.getHomeBand(), ProblemType.PROVISIONAL_WINNER_CHECK, station));
                        if (SimulatorUtils.isFeasible(result)) {
                            endOfStageParticipation.setParticipation(station, Participation.FROZEN_PENDING_CATCH_UP);
                        }
                    }
                }

                if (!parameters.isEarlyStopping()) {
                    // Don't remove unconstrained stations if we're going to possibly early terminate. They could still be useful!
                    for (final IStationInfo station : checkUnconstrained) {
                        if (unconstrainedChecker.isUnconstrained(station, ladder)) {
                            final SimulatorResult feasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, station.getHomeBand(), ProblemType.NOT_NEEDED_UPDATE));
                            Preconditions.checkState(SimulatorUtils.isFeasible(feasibility), "NOT NEEDED station couldn't exit feasibly!");
                            MultiBandSimulator.exitStation(station, Participation.EXITED_NOT_NEEDED, feasibility.getSATFCResult().getWitnessAssignment(), participation, ladder, state.getPrices());
                        }
                    }
                }

                final Map<IStationInfo, CatchupPoint> catchupPoints = state.getCatchupPoints();
                if (endOfStageParticipation.getMatching(Participation.FROZEN_PENDING_CATCH_UP).size() > 0) {
                    final double maxCatchupPoint = endOfStageParticipation.getMatching(Participation.FROZEN_PENDING_CATCH_UP).stream().mapToDouble(s -> catchupPoints.get(s).getCatchUpPoint()).max().getAsDouble();
                    state.setBaseClockPrice(maxCatchupPoint);
                    log.info("Base clock price is reset to {}", maxCatchupPoint);
                }
            }
            stateSaver.saveState(stationDB, state);
            timeTrackingDecorator.report();

            while (true) {
                state = simulator.executeRound(state);
                timeTrackingDecorator.report();
                stateSaver.saveState(stationDB, state);

                if (state.isEarlyStopped()) {
                    log.info("Early termination of stage");
                    break;
                }

                // If, after processing the bids from a round, every participating station has either exited or become provisionally winning, the stage ends
                if (state.getParticipation().getMatching(Participation.INACTIVE).equals(state.getLadder().getStations())) {
                    log.info("All stations are inactive. Ending stage");
                    log.info("Provisional winner counts (home band): {}", state.getParticipation().getMatching(Participation.FROZEN_PROVISIONALLY_WINNING).stream().collect(groupingBy(IStationInfo::getHomeBand, counting())));
                    break;
                }
            }

            if (!state.isEarlyStopped() && parameters.getForwardAuctionAmounts().size() >= roundTracker.getStage()) {
                AuctionResult temp = getAuctionResult(state);
                long forwardAuctionCost = parameters.getForwardAuctionAmounts().get(roundTracker.getStage() - 1);
                log.info("The forward auction was willing to pay {} and the clearing cost was {}", Humanize.spellBigNumber(parameters.getForwardAuctionAmounts().get(roundTracker.getStage() - 1)), Humanize.spellBigNumber(temp.getCost()));
                if (temp.getCost() <= forwardAuctionCost) {
                    log.info("Therefore, ending auction");
                    break;
                } else {
                    log.info("Failed final stage rule");
                    if (clearingTarget == stages.get(stages.size() - 1)) {
                        auctionSuccessful = false;
                        log.info("This was the final stage! The auction was not successful and no spectrum will be cleared");
                    } else {
                        log.info("Moving to next stage");
                    }
                }
            }
            roundTracker.incrementStage();
        }

        if (auctionSuccessful) {
            AuctionResult auctionResult = getAuctionResult(state);
            log.info("Final cost of reverse auction {}. Final value loss is {}", auctionResult.getCost(), auctionResult.getValueLoss());
        }

        if (parameters.getBidProcessingAlgorithmParameters().getDistributedFeasibilitySolver() != null) {
            // TODO: Some sort of finally block?
            parameters.getBidProcessingAlgorithmParameters().getExecutorService().shutdownNow();
            parameters.getBidProcessingAlgorithmParameters().getDistributedFeasibilitySolver().close();
        }
        log.info("Ending simulation");
        log.info("Finished. Goodbye :)");
    }

    public static AuctionResult getAuctionResult(LadderAuctionState state) {
        final Map<IStationInfo, Long> finalPrices = state.getPrices();
        final IModifiableLadder finalLadder = state.getLadder();
        final Set<IStationInfo> winners = state.getParticipation().getMatching(Participation.FROZEN_PROVISIONALLY_WINNING);
        final long reverseAuctionCost = winners.stream().mapToLong(finalPrices::get).sum();
        final long valueLoss = winners.stream().mapToLong(s -> s.getValue() - s.getValue(finalLadder.getStationBand(s))).sum();
        return new AuctionResult(reverseAuctionCost, valueLoss);
    }

    @Data
    public static class AuctionResult {
        final long cost;
        final long valueLoss;
    }

    private static void betweenStageClearingTargetOptimization(MultiBandSimulatorParameters parameters, IPreviousAssignmentHandler previousAssignmentHandler, IModifiableLadder ladder, IStationDB.IModifiableStationDB stationDB, UHFCachingFeasibilitySolverDecorator uhfCache, Set<IStationInfo> impairingStations) {
        log.info("Dumping back as many impairing stations as possible");
        final Map<Integer, Set<Integer>> otherDomains = ladder.getBandStations(Band.UHF).stream().filter(s -> !s.isImpaired()).collect(toMap(IStationInfo::getId, s -> s.getDomain(Band.UHF)));
        final Map<Integer, Integer> previousAssignment = new HashMap<>(ladder.getPreviousAssignment());
        impairingStations.forEach(s -> previousAssignment.put(s.getId(), ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL));
        final Map<Integer, Integer> newStageAssignment = clearingTargetOptimization(stationDB, parameters, impairingStations, impairingStations.size(), otherDomains, previousAssignment);
        final Set<IStationInfo> noLongerImpairing = impairingStations.stream().filter(s -> newStageAssignment.get(s.getId()) != ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL).collect(toSet());
        impairingStations.removeAll(noLongerImpairing);
        if (!noLongerImpairing.isEmpty()) {
            log.info("Un-impairing the following stations: {}. {} impairing stations remain", noLongerImpairing, impairingStations.size());
            previousAssignmentHandler.updatePreviousAssignment(Maps.filterValues(newStageAssignment, v -> v != ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL));
            noLongerImpairing.forEach(IStationInfo::unimpair);
            parameters.getEventBus().post(new DomainChangeEvent(ladder, parameters.getConstraintManager()));
            if (uhfCache != null) {
                // You could be smarter here, but it probably isn't worth the effort
                uhfCache.clear();
            }
        } else {
            log.info("Could not un-impair any stations");
        }
    }

    @Value
    public static class OpeningPrices {
        private final IPrices<Double> benchmarkPrices;
        private final IPrices<Long> actualPrices;
    }

    public static OpeningPrices setOpeningPrices(MultiBandSimulatorParameters parameters, double UHFToOff) {
        final IPrices<Double> benchmarkPrices = new PricesImpl<>();
        final IPrices<Long> actualPrices = new PricesImpl<>();

        // Mess around and only raise the OFF price
        final Map<Band, Double> openingPricesPerUnitVolume = parameters.getFixedOpeningBenchmarkPrices(UHFToOff);
        final Map<Band, Double> fccOpeningPricesPerUnitVolume = parameters.getFCCOpeningBenchmarkPrices();

        final IStationDB stationDB = parameters.getStationDB();

        for (final IStationInfo s : stationDB.getStations()) {
            if (s.getNationality().equals(Nationality.CA)) {
                continue;
            }
            Map<Band, Double> perUnits = s.getHomeBand().isVHF() ? fccOpeningPricesPerUnitVolume : openingPricesPerUnitVolume;
            // In round 0, the benchmark prices of all stations are set equal to the opening benchmark prices of UHF stations, irrespective of home band
            for (Band band : parameters.getAuctionBands()) {
                benchmarkPrices.setPrice(s, band, perUnits.get(band));
            }
            for (Band band : parameters.getAuctionBands()) {
                actualPrices.setPrice(s, band, SimulatorUtils.benchmarkToActualPrice(s, band, benchmarkPrices.getOffers(s)));
            }
        }
        return new OpeningPrices(benchmarkPrices, actualPrices);
    }

    public static void adjustCT(int ct, IStationDB.IModifiableStationDB stationDB, EventBus eventBus, ILadder ladder, IConstraintManager constraintManager) {
        SimulatorUtils.adjustCTSimple(ct, stationDB);
        eventBus.post(new DomainChangeEvent(ladder, constraintManager));
    }

    public static Map<Integer, Integer> clearingTargetOptimization(IStationDB stationDB, SimulatorParameters parameters, Set<IStationInfo> possibleToImpair) {
        return clearingTargetOptimization(stationDB, parameters, possibleToImpair, null, new HashMap<>(), new HashMap<>());
    }

    public static Map<Integer, Integer> clearingTargetOptimization(IStationDB stationDB, SimulatorParameters parameters, Set<IStationInfo> possibleToImpair, Integer currentSolution, Map<Integer, Set<Integer>> otherDomains, Map<Integer, Integer> startingAssignment) {
        try {
            final VCGMip.IMIPEncoder clearingTargetOptimizationMIP = parameters.getUseNewMIP() ? new ClearingTargetOptimizationMIP_2() : new ClearingTargetOptimizationMIP();
            final VCGMip.MIPMaker mipMaker;
            mipMaker = new VCGMip.MIPMaker(stationDB, parameters.getStationManager(), parameters.getConstraintManager(), clearingTargetOptimizationMIP);
            // Need to make sure you don't query impaired domain here, so temporarily unimpair the stations so you can get real domains...
            final Set<IStationInfo> actuallyImpaired = possibleToImpair.stream().filter(IStationInfo::isImpaired).collect(toSet());
            actuallyImpaired.stream().forEach(IStationInfo::unimpair);
            final Map<Integer, Set<Integer>> domains = possibleToImpair
                    .stream().collect(toMap(
                            IStationInfo::getId,
                            s -> Sets.union(s.getDomain(s.getHomeBand()), ImmutableSet.of(ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL)))
                    );
            domains.putAll(otherDomains);
            actuallyImpaired.stream().forEach(IStationInfo::impair);
            final VCGMip.MIPResult phaseOneResult = mipMaker.solve(domains, domains.keySet(), parameters.getMipCutoff(), parameters.getSeed(), parameters.getParallelism(), false, null, startingAssignment, false);
            if (currentSolution == null) {
                abortIfNecessary(phaseOneResult);
            }
            final int nImpairingStations = (int) Math.round(phaseOneResult.getObjectiveValue());
            log.info("{} stations will be impairing", nImpairingStations);
            if (parameters.getUseNewMIP() || (currentSolution != null && currentSolution == nImpairingStations) || nImpairingStations == 0) {
                // No point in going to phase 2, we didn't find anything better than we already knew about
                log.debug("Skipping phase 2 of clearing target optimization");
                return phaseOneResult.getAssignment();
            } else {
                log.info("Now finding best set (by minimizing pop of impairing stations)");
                final ClearingTargetOptimizationMIP clearingTargetOptimizationMIPPhaseTwo = new ClearingTargetOptimizationMIP(nImpairingStations);
                final VCGMip.MIPMaker mipMakerPhaseTwo = new VCGMip.MIPMaker(stationDB, parameters.getStationManager(), parameters.getConstraintManager(), clearingTargetOptimizationMIPPhaseTwo);
                final VCGMip.MIPResult phaseTwoResult = mipMakerPhaseTwo.solve(domains, domains.keySet(), parameters.getMipCutoff(), parameters.getSeed(), parameters.getParallelism(), false, null, phaseOneResult.getAssignment(), false);
                if (currentSolution != null) {
                    abortIfNecessary(phaseTwoResult);
                }
                Preconditions.checkState(phaseTwoResult.getAssignment().values().stream().filter(c -> c == ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL).count() <= nImpairingStations, "Phase 2 result has more impairing stations than phase 1 said was necessary");
                return phaseTwoResult.getAssignment();
            }
        } catch (IloException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void abortIfNecessary(VCGMip.MIPResult result) {
        if (!(result.getStatus().equals(IloCplex.Status.Feasible) || result.getStatus().equals(IloCplex.Status.Optimal))) {
            throw new IllegalStateException("Could not find any feasible way to set up the non-participating stations!. Aborting the auction");
        }
    }


}
