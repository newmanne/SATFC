package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.aeatk.misc.cputime.CPUTime;
import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IProblemMaker;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.ProblemMakerImpl;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.ladder.IModifiableLadder;
import ca.ubc.cs.beta.fcc.simulator.ladder.LadderEventOnMoveDecorator;
import ca.ubc.cs.beta.fcc.simulator.ladder.SimpleLadder;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameter;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prevassign.IPreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.prevassign.SimplePreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.prices.PricesImpl;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SimulatorResult;
import ca.ubc.cs.beta.fcc.simulator.solver.decorator.*;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.ProblemType;
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
import ca.ubc.cs.beta.fcc.simulator.valuations.MaxCFStickValues;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import humanize.Humanize;
import lombok.Builder;
import lombok.Cleanup;
import lombok.Data;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collector;
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

        IModifiableLadder ladder = new SimpleLadder(Arrays.asList(Band.values()), previousAssignmentHandler);
        ladder = new LadderEventOnMoveDecorator(ladder, parameters.getEventBus());

        final RoundTracker roundTracker = new RoundTracker();

        final IProblemMaker problemMaker = new ProblemMakerImpl(ladder, parameters.createProblemSpecGenerator(), roundTracker);

        log.info("Reading station info from file");
        final IStationDB.IModifiableStationDB stationDB = parameters.getStationDB();

        // Initialize opening benchmarkPrices
        log.info("Setting opening prices");
        final OpeningPrices setOpeningPrices = setOpeningPrices(parameters);
        final IPrices actualPrices = setOpeningPrices.getActualPrices();
        final IPrices benchmarkPrices = setOpeningPrices.getBenchmarkPrices();

        log.info("Assigning valuations to stations");
        final MaxCFStickValues maxCFStickValues = new MaxCFStickValues(parameters.getMaxCFStickFile(), stationDB, parameters.getValuesSeed());
        final Map<IStationInfo, MaxCFStickValues.IValueGenerator> stationToGenerator = maxCFStickValues.get();
        final Collection<IStationInfo> americanStations = stationDB.getStations(Nationality.US);
        final Set<IStationInfo> removed = new HashSet<>();
        for (final IStationInfo station : americanStations) {
            final MaxCFStickValues.IValueGenerator iValueGenerator = stationToGenerator.get(station);
            if (iValueGenerator == null) {
                log.info("No valuation model for station {}, skipping", station);
                stationDB.removeStation(station.getId());
                removed.add(station);
                continue;
            }
//            Double value = null;
//            int onCount = 0;
//            int n = 1000;
//            for (int i = 0; i < n; i++) {
//                value = iValueGenerator.generateValue();
//                if (value < actualPrices.getPrice(station, Band.OFF)) {
//                    onCount++;
//                }
//            }
//            log.info("Station {} participates {} of the time", station.getId(), Humanize.formatPercent(onCount / (double) n));

            double value = iValueGenerator.generateValue();
            final Map<Band, Double> valueMap = new HashMap<>();
            double frac = 1.0;
            for (Band band : station.getHomeBand().getBandsBelowInclusive(false)) {
                if (band.equals(Band.OFF)) {
                    continue;
                }
                // UHF gets full value for home, 2/3 for HVHF, 1/3 for LVHF, then 0
                valueMap.put(band, value * frac);
                frac -= 1. / 3;
            }
            valueMap.put(Band.OFF, 0.); // Do this explicitly to not have any floating point nonsense
            ((StationInfo) station).setValues(valueMap);
        }
        final Map<Band, List<IStationInfo>> droppedStationToBand = removed.stream().collect(Collectors.groupingBy(IStationInfo::getHomeBand));
        for (Map.Entry<Band, List<IStationInfo>> entry : droppedStationToBand.entrySet()) {
            log.info("Dropped {} {} stations due to not having valuations", entry.getValue().size(), entry.getKey());
        }

        log.info("Figuring out participation");
        // Figure out participation
        ParticipationRecord participation = new ParticipationRecord(stationDB, parameters.getParticipationDecider(actualPrices));
        // TODO: Right now, I'm dumping any active station into "OFF" (e.g. even if they are only considering a move to LV). This is an oversimplification.
        for (final IStationInfo s : stationDB.getStations()) {
            ladder.addStation(s, Participation.EXITED.contains(participation.getParticipation(s)) ? s.getHomeBand() : Band.OFF);
        }

        log.info("Building solver");
        IFeasibilitySolver tmp = parameters.createSolver();

        GreedyFlaggingDecorator greedyFlaggingDecorator = null;
        if (parameters.isGreedyFirst()) {
            greedyFlaggingDecorator = new GreedyFlaggingDecorator(tmp, parameters.getConstraintManager());
            tmp = greedyFlaggingDecorator;
        }

        UHFCachingFeasibilitySolverDecorator uhfCache = null;
        if (parameters.isUHFCache()) {
            uhfCache = new UHFCachingFeasibilitySolverDecorator(tmp, participation, problemMaker, parameters.isLazyUHF(), ladder, parameters.getConstraintManager());
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

        final long notParticipatingUS = stationDB.getStations(Nationality.US).stream()
                .map(participation::getParticipation)
                .filter(p -> p.equals(Participation.EXITED_NOT_PARTICIPATING))
                .count();
        final long totalUS = stationDB.getStations().stream().filter(s -> s.getNationality().equals(Nationality.US)).count();
        log.info("There are {} participating and {} non-participating US stations out of {} US stations", totalUS - notParticipatingUS, notParticipatingUS, totalUS);

        if (notParticipatingUS == totalUS) {
            log.warn("No one is participating in the auction! Ending");
            return;
        }

        final Set<IStationInfo> onAirStations = participation.getOnAirStations();
        log.info("Finding an initial assignment for the {} initially on air stations", onAirStations.size());

        /**
         * Now we go through clearing targets, in descending order, and try to find out whether we can even begin the auction by finding a satisfying assignment
         * We choose a clearing target as the largest target that can be cleared given participation
         */
        ClearingResult result = null;
        for (final int maxChannel : SimulatorUtils.CLEARING_TARGETS.reverse()) {
            log.info("Trying clearing target of {}", maxChannel);
            adjustCT(maxChannel, stationDB);
            final ClearingResult tempResult = testClearingTarget(ladder, solver, problemMaker, maxChannel);
            if (!tempResult.isFeasible()) {
                log.info("Failed to clear at {}", maxChannel);
                break; // Use previous value
            } else {
                result = tempResult;
            }
        }
        Preconditions.checkNotNull(result, "Could not clear the opening at ANY clearing target...");
        log.info("Finalizing clearing target at {}", result.getClearingTarget());
        adjustCT(result.getClearingTarget(), stationDB);
        if (greedyFlaggingDecorator != null) {
            greedyFlaggingDecorator.init(ladder);
        }
        if (uhfCache != null) {
            uhfCache.init(ladder, parameters.getConstraintManager());
        }
        // This is a bit awkward (Should go through the ladder... but oh well)
        result.getAssignment().entrySet().forEach(bandAssignmentEntry -> previousAssignmentHandler.updatePreviousAssignment(bandAssignmentEntry.getValue()));
        int clearingTarget = result.getClearingTarget();

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

        final ISimulatorUnconstrainedChecker unconstrainedChecker = parameters.getUnconstrainedChecker(participation);

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
        IPrices benchmarkPrices = new PricesImpl();
        IPrices actualPrices = new PricesImpl();
        Map<Band, Double> openingPricesPerUnitVolume = parameters.getOpeningBenchmarkPrices();
        final IStationDB stationDB = parameters.getStationDB();
        for (final IStationInfo s : stationDB.getStations()) {
            if (s.getNationality().equals(Nationality.CA)) {
                continue;
            }
            benchmarkPrices.setPrice(s, s.getHomeBand(), 0.);
            actualPrices.setPrice(s, s.getHomeBand(), 0.);
            Arrays.stream(Band.values()).filter(b -> b.isBelow(s.getHomeBand())).forEach(band -> {
                final double price;
                if (s.getHomeBand().equals(Band.UHF)) {
                    price = openingPricesPerUnitVolume.get(band);
                } else if (s.getHomeBand().equals(Band.HVHF)) {
                    if (band.equals(Band.OFF)) {
                        price = openingPricesPerUnitVolume.get(Band.OFF) - openingPricesPerUnitVolume.get(Band.HVHF);
                    } else {
                        price = openingPricesPerUnitVolume.get(Band.LVHF) - openingPricesPerUnitVolume.get(Band.HVHF);
                    }
                } else if (s.getHomeBand().equals(Band.LVHF)) {
                    price = openingPricesPerUnitVolume.get(Band.OFF) - openingPricesPerUnitVolume.get(Band.LVHF);
                } else {
                    throw new IllegalStateException();
                }
                benchmarkPrices.setPrice(s, band, price);
                if (band.equals(Band.OFF)) {
                    log.debug("Station: {}, Price: {}, Volume: {}, Actual price: {}", s, Humanize.spellBigNumber(price), Humanize.spellBigNumber(s.getVolume()), Humanize.spellBigNumber(price * s.getVolume()));
                }
                actualPrices.setPrice(s, band, price * s.getVolume());
            });
        }
        return new OpeningPrices(benchmarkPrices, actualPrices);
    }

    public static void adjustCT(int ct, IStationDB.IModifiableStationDB stationDB) {
        // "Apply" the new clearing target to anything that was maintaining max channel state
        // WARNING: This can lead to a lot of strange bugs if something queries a station's domain and stores it before CT is finalized...
        BandHelper.setUHFChannels(ct);
        for (IStationInfo s : stationDB.getStations()) {
            ((StationInfo) s).adjustDomain(s.getNationality().equals(Nationality.CA) ? ct - 1 : ct);
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

    public static ClearingResult testClearingTarget(ILadder ladder, IFeasibilitySolver solver, IProblemMaker problemMaker, int maxChannel) {
        final Map<Band, Map<Integer, Integer>> bandAssignmentMap = new HashMap<>();
        for (Band band : ladder.getAirBands()) {
            final Set<IStationInfo> bandStations = ladder.getBandStations(band);
            if (bandStations.size() > 0) {
                final SimulatorResult initialFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(bandStations, band, ProblemType.INITIAL_PLACEMENT, null));
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
        return ClearingResult.builder()
                .feasible(true)
                .clearingTarget(maxChannel)
                .assignment(bandAssignmentMap)
                .build();
    }

}
