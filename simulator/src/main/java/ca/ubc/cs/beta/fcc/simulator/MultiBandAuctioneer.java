package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.aeatk.misc.cputime.CPUTime;
import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.fcc.simulator.clearingtargetoptimization.ClearingTargetOptimizationMIP;
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
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.ISimulatorUnconstrainedChecker;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.fcc.simulator.vacancy.IVacancyCalculator;
import ca.ubc.cs.beta.fcc.simulator.vacancy.ParallelVacancyCalculator;
import ca.ubc.cs.beta.fcc.vcg.VCGMip;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import ilog.cplex.IloCplex;
import lombok.Builder;
import lombok.Cleanup;
import lombok.Data;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

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

        IModifiableLadder ladder = new SimpleLadder(parameters.getAuctionBands(), previousAssignmentHandler);
        ladder = new LadderEventOnMoveDecorator(ladder, parameters.getEventBus());

        final RoundTracker roundTracker = new RoundTracker();

        final IProblemMaker problemMaker = new ProblemMakerImpl(ladder, parameters.createProblemSpecGenerator(), roundTracker);

        log.info("Reading station info from file");
        final IStationDB.IModifiableStationDB stationDB = parameters.getStationDB();

        SimulatorUtils.assignValues(parameters);

        if (parameters.getMaxChannel() != null) {
            adjustCT(parameters.getMaxChannel(), stationDB);
        }
        if (parameters.getCity() != null) {
            new SimulatorParameters.CityAndLinks(parameters.getCity(), parameters.getNLinks(), stationDB, parameters.getConstraintManager()).function();
        }

        // Initialize opening benchmarkPrices
        log.info("Setting opening prices");
        final OpeningPrices setOpeningPrices = setOpeningPrices(parameters);
        final IPrices actualPrices = setOpeningPrices.getActualPrices();
        final IPrices benchmarkPrices = setOpeningPrices.getBenchmarkPrices();

        log.info("Figuring out participation");
        // Figure out participation
        ParticipationRecord participation = new ParticipationRecord(stationDB, parameters.getParticipationDecider(actualPrices));
        // This dumps every participating station into OFF. This is fine (provided that the participation decider takes into account that the original OFF offer gives a positive utility), but is an oversimplification due to not simulating the initial clearing target optimization
        for (final IStationInfo s : stationDB.getStations()) {
            ladder.addStation(s, Participation.EXITED.contains(participation.getParticipation(s)) ? s.getHomeBand() : Band.OFF);
        }

        log.info("Building solver");
        IFeasibilitySolver tmp = new VoidFeasibilitySolver();

        if (!parameters.isGreedyOnly()) {
            tmp = new SequentialSolverDecorator(tmp, parameters.createSolver());
        }

        GreedyFlaggingDecorator greedyFlaggingDecorator = null;
        if (parameters.isGreedyFirst()) {
            greedyFlaggingDecorator = new GreedyFlaggingDecorator(tmp, parameters.getConstraintManager());
            tmp = greedyFlaggingDecorator;
        }

        UHFCachingFeasibilitySolverDecorator uhfCache = null;
        if (parameters.isUHFCache()) {
            uhfCache = new UHFCachingFeasibilitySolverDecorator(tmp, participation, problemMaker, parameters.isLazyUHF(), parameters.isRevisitTimeouts(), ladder, parameters.getConstraintManager());
            parameters.getEventBus().register(uhfCache);
            tmp = uhfCache;
        }

        final FeasibilityResultDistributionDecorator.FeasibilityResultDistribution feasibilityResultDistribution = new FeasibilityResultDistributionDecorator.FeasibilityResultDistribution();
        tmp = new FeasibilityResultDistributionDecorator(tmp, feasibilityResultDistribution);
        parameters.getEventBus().register(tmp);

        final ProblemSaverDecorator problemSaverDecorator = new ProblemSaverDecorator(tmp, parameters.getProblemFolder());
        parameters.getEventBus().register(problemSaverDecorator);
        tmp = problemSaverDecorator;

        final TimeTrackerFeasibilitySolverDecorator timeTrackingDecorator = new TimeTrackerFeasibilitySolverDecorator(tmp, simulatorWatch, simulatorCPU);
        tmp = timeTrackingDecorator;
        parameters.getEventBus().register(tmp);

        @Cleanup
        final IFeasibilitySolver solver = tmp;


        final Set<IStationInfo> nonParticipatingUSStations = stationDB.getStations(Nationality.US).stream()
                .filter(s -> participation.getParticipation(s).equals(Participation.EXITED_NOT_PARTICIPATING)).collect(Collectors.toSet());

        final long notParticipatingUS = nonParticipatingUSStations.size();
        final long totalUS = stationDB.getStations().stream().filter(s -> s.getNationality().equals(Nationality.US)).count();
        log.info("There are {} participating and {} non-participating US stations out of {} US stations", totalUS - notParticipatingUS, notParticipatingUS, totalUS);

        if (notParticipatingUS == totalUS) {
            log.warn("No one is participating in the auction! Ending");
            return;
        }

        final Set<IStationInfo> onAirStations = participation.getOnAirStations();
        log.info("Finding an initial assignment for the {} initially on air stations ({} US UHF, {} US VHF)", onAirStations.size(), nonParticipatingUSStations.stream().filter(s -> s.getHomeBand().equals(Band.UHF)).count(), nonParticipatingUSStations.stream().filter(s -> !s.getHomeBand().equals(Band.UHF)).count());

        final int clearingTarget = parameters.getMaxChannel();
        final int impairingAllowedStart = clearingTarget + 3;
        final Set<Integer> impairingChannels = StationPackingUtils.UHF_CHANNELS.stream().filter(c -> c >= impairingAllowedStart).collect(GuavaCollectors.toImmutableSet());
        final ClearingTargetOptimizationMIP clearingTargetOptimizationMIP = new ClearingTargetOptimizationMIP(impairingChannels);
        final VCGMip.MIPMaker mipMaker = new VCGMip.MIPMaker(stationDB, parameters.getStationManager(), parameters.getConstraintManager(), clearingTargetOptimizationMIP);
        final Map<Integer, Set<Integer>> domains = onAirStations
                .stream().collect(GuavaCollectors.toImmutableMap(
                                IStationInfo::getId,
                                s -> Sets.union(s.getDomain(s.getHomeBand()), Sets.intersection(impairingChannels, parameters.getStationManager().getDomain(new Station(s.getId())))))
                );
        final VCGMip.MIPResult mipResult = mipMaker.solve(domains, onAirStations.stream().map(IStationInfo::getId).collect(Collectors.toSet()), parameters.getMipCutoff(), parameters.getSeed(), parameters.getParallelism(), false, null);
        if (!(mipResult.getStatus().equals(IloCplex.Status.Feasible) || mipResult.getStatus().equals(IloCplex.Status.Optimal))) {
            log.warn("Could not find any feasible way to set up the non-participating stations!. Aborting the auction");
            return;
        }
        final Map<Integer, Integer> assignment = mipResult.getAssignment();

//        // TODO: START CHANGE
//        /**
//         * Now we go through clearing targets, in order from most aggressive to least aggressive, and try to find out whether we can even begin the auction by finding a satisfying assignment
//         * We choose a clearing target as the largest target that can be cleared given participation
//         */
//        ClearingResult result = null;
//        final List<Integer> clearingTargets = parameters.getMaxChannel() != null ? Lists.newArrayList(parameters.getMaxChannel()) : SimulatorUtils.CLEARING_TARGETS;
//
//        // Use a separate solver for clearing target initialization procedure. Of course, this means...
//        @Cleanup
//        final IFeasibilitySolver ctSolver = new LocalFeasibilitySolver(new SATFCFacadeBuilder().build());
//        for (final int maxChannel : clearingTargets) {
//            log.info("Trying clearing target of {}", maxChannel);
//            adjustCT(maxChannel, stationDB);
//            result = testClearingTarget(ladder, ctSolver, problemMaker, maxChannel, parameters.getStartingAssignment());
//            if (result.isFeasible()) {
//                break;
//            } else {
//                log.info("Failed to clear at {}", maxChannel);
//            }
//        }
//        Preconditions.checkState(result != null && result.isFeasible(), "Could not clear the opening at clearing targets %s", clearingTargets);
//        int clearingTarget = result.getClearingTarget();
//        log.info("Finalizing clearing target at {}", clearingTarget);

        adjustCT(clearingTarget, stationDB);
        // Restrict any impairing stations to the impairing channel they are on
        int impairingCount = 0;
        final Set<IStationInfo> toRemove = new HashSet<>();
        for (Map.Entry<Integer, Integer> entry : assignment.entrySet()) {
            int channel = entry.getValue();
            if (channel > clearingTarget) {
                impairingCount++;
                int station = entry.getKey();
                ((StationInfo) stationDB.getStationById(station)).setMaxChannel(channel);
                ((StationInfo) stationDB.getStationById(station)).setMinChannel(channel);
            }
        }
        log.info("There are {} impairing stations out of {} UHF non-participating stations. Removing them from auction", impairingCount, onAirStations.size());

        if (greedyFlaggingDecorator != null) {
            greedyFlaggingDecorator.init(ladder);
        }
        if (uhfCache != null) {
            uhfCache.init(ladder, parameters.getConstraintManager());
        }
        // This is a bit awkward (Should go through the ladder... but oh well)
//        assignment.entrySet().forEach(bandAssignmentEntry -> previousAssignmentHandler.updatePreviousAssignment(bandAssignmentEntry.getValue()));
        previousAssignmentHandler.updatePreviousAssignment(assignment);

        final ProblemSaverDecorator.ProblemSaverInfo problemSaverInfo = ProblemSaverDecorator.ProblemSaverInfo.builder()
                .interference(parameters.getConstraintSet())
                .maxChannel(clearingTarget)
                .build();
        problemSaverDecorator.writeInfo(problemSaverInfo);
        problemSaverDecorator.writeStartingAssignment(ladder.getPreviousAssignment());

        final Map<IStationInfo, Double> initialCompensations = new HashMap<>();
        for (IStationInfo s : ladder.getStations()) {
            if (Participation.NON_ZERO_PRICES.contains(participation.getParticipation(s))) {
                initialCompensations.put(s, actualPrices.getPrice(s, ladder.getStationBand(s)));
            } else {
                initialCompensations.put(s, 0.);
            }
        }

        LadderAuctionState state = LadderAuctionState.builder()
                .benchmarkPrices(benchmarkPrices)
                .participation(participation)
                .round(roundTracker.getRound())
                .assignment(ladder.getPreviousAssignment())
                .ladder(ladder)
                .prices(initialCompensations)
                .baseClockPrice(parameters.getOpeningBenchmarkPrices().get(Band.OFF))
                .offers(actualPrices)
                .build();

        final IVacancyCalculator vacancyCalculator = new ParallelVacancyCalculator(
                participation,
                parameters.getConstraintManager(),
                parameters.getVacFloor(),
                Runtime.getRuntime().availableProcessors()
        );

        final ISimulatorUnconstrainedChecker unconstrainedChecker = parameters.getUnconstrainedChecker(participation, ladder);

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
                        .build()
        );

        log.info("Starting simulation!");

        stateSaver.saveState(stationDB, state);
        timeTrackingDecorator.report();

        while (true) {
            state = simulator.executeRound(state);
            timeTrackingDecorator.report();
            stateSaver.saveState(stationDB, state);
            // If, after processing the bids from a round, every participating station has either exited or become provisionally winning, the stage ends
            if (state.getParticipation().getMatching(Participation.INACTIVE).equals(state.getLadder().getStations())) {
                log.info("All stations are inactive. Ending simulation");
                break;
            }
        }

        log.info("Finished. Goodbye :)");
    }

    @Value
    public static class OpeningPrices {
        private final IPrices benchmarkPrices;
        private final IPrices actualPrices;
    }

    public static OpeningPrices setOpeningPrices(MultiBandSimulatorParameters parameters) {
        final IPrices benchmarkPrices = new PricesImpl();
        final IPrices actualPrices = new PricesImpl();
        final Map<Band, Double> openingPricesPerUnitVolume = parameters.getOpeningBenchmarkPrices();
        final IStationDB stationDB = parameters.getStationDB();

        for (final IStationInfo s : stationDB.getStations()) {
            if (s.getNationality().equals(Nationality.CA)) {
                continue;
            }
            // In round 0, the benchmark prices of all stations are set equal to the opening benchmark prices of UHF stations, irrespective of home band
            for (Band band : parameters.getAuctionBands()) {
                benchmarkPrices.setPrice(s, band, openingPricesPerUnitVolume.get(band));
            }
            for (Band band : parameters.getAuctionBands()) {
                actualPrices.setPrice(s, band, SimulatorUtils.benchmarkToActualPrice(s, band, benchmarkPrices.getOffers(s)));
            }
        }
        return new OpeningPrices(benchmarkPrices, actualPrices);
    }

    public static void adjustCT(int ct, IStationDB.IModifiableStationDB stationDB) {
        // "Apply" the new clearing target to anything that was maintaining max channel state
        // WARNING: This can lead to a lot of strange bugs if something queries a station's domain and stores it before CT is finalized...
        BandHelper.setUHFChannels(ct);
        for (IStationInfo s : stationDB.getStations()) {
            ((StationInfo) s).setMaxChannel(s.getNationality().equals(Nationality.CA) ? ct - 1 : ct);
        }
    }

    @Data
    @Builder
    public static class ClearingResult {
        private Map<Band, Map<Integer, Integer>> assignment;
        private boolean feasible;
        private int clearingTarget;

        public static ClearingResult infeasible() {
            return ClearingResult.builder().feasible(false).build();
        }
    }

    public static ClearingResult testClearingTarget(ILadder ladder, IFeasibilitySolver solver, IProblemMaker problemMaker, int maxChannel, Map<Integer, Integer> previousAssignment) {
        final Map<Band, Map<Integer, Integer>> bandAssignmentMap = new HashMap<>();
        for (final Band band : ladder.getAirBands()) {
            final Set<IStationInfo> bandStations = ladder.getBandStations(band);
            if (bandStations.size() > 0) {
                final SimulatorProblem simulatorProblem = problemMaker.makeProblem(bandStations, band, ProblemType.INITIAL_PLACEMENT, null);

                simulatorProblem.getSATFCProblem().getProblem().setPreviousAssignment(previousAssignment);

                final SimulatorResult initialFeasibility = solver.getFeasibilityBlocking(simulatorProblem);
                if (SimulatorUtils.isFeasible(initialFeasibility)) {
                    final Map<Integer, Integer> assignment = initialFeasibility.getSATFCResult().getWitnessAssignment();
                    log.info("Found an initial assignment for the {} non-participating stations in band {}", bandStations.size(), band);
                    bandAssignmentMap.put(band, assignment);
                } else {
                    log.info("Initial non-participating stations in {} do not have a feasible assignment with CT of {}! (Result was {})", band, maxChannel, initialFeasibility.getSATFCResult().getResult());
                    return ClearingResult.infeasible();
                }
            }
        }
        log.info("{} is a feasible clearing target given participation", maxChannel);
        return ClearingResult.builder()
                .feasible(true)
                .clearingTarget(maxChannel)
                .assignment(bandAssignmentMap)
                .build();
    }

}
