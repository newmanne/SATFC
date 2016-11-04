package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.aeatk.misc.cputime.CPUTime;
import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IProblemMaker;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.ProblemMakerImpl;
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
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.ISimulatorUnconstrainedChecker;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.fcc.simulator.vacancy.IVacancyCalculator;
import ca.ubc.cs.beta.fcc.simulator.vacancy.ParallelVacancyCalculator;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import humanize.Humanize;
import lombok.Cleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
        final StationDB stationDB = parameters.getStationDB();

        // Initialize opening benchmarkPrices
        log.info("Setting opening prices");
        IPrices benchmarkPrices = new PricesImpl();
        IPrices actualPrices = new PricesImpl();
        final Map<Band, Double> openingPricesPerUnitVolume = parameters.getOpeningBenchmarkPrices();
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
                    log.info("Station: {}, Price: {}, Volume: {}, Actual price: {}", s, Humanize.spellBigNumber(price), Humanize.spellBigNumber(s.getVolume()), Humanize.spellBigNumber(price * s.getVolume()));
                }
                actualPrices.setPrice(s, band, price * s.getVolume());
            });
        }

        // TODO: Right now, I'm dumping any active station into "OFF" (e.g. even if they are only considering a move to LV). This is an oversimplification.
        log.info("Figuring out participation");
        // Figure out participation
        ParticipationRecord participation = new ParticipationRecord(stationDB, parameters.getParticipationDecider(actualPrices));

        for (IStationInfo s: stationDB.getStations()) {
            ladder.addStation(s, Participation.EXITED.contains(participation.getParticipation(s)) ? s.getHomeBand() : Band.OFF);
        }

        log.info("Building solver");
        IFeasibilitySolver tmp = parameters.createSolver();

        if (parameters.isGreedyFirst()) {
            tmp = new GreedyFlaggingDecorator(tmp, ladder, parameters.getConstraintManager());
        }

        UHFCachingFeasibilitySolverDecorator uhfCache = new UHFCachingFeasibilitySolverDecorator(tmp, participation, problemMaker, parameters.isLazyUHF(), ladder, parameters.getConstraintManager());
        parameters.getEventBus().register(uhfCache);
        tmp = uhfCache;

        final FeasibilityResultDistributionDecorator.FeasibilityResultDistribution feasibilityResultDistribution = new FeasibilityResultDistributionDecorator.FeasibilityResultDistribution();
        tmp = new FeasibilityResultDistributionDecorator(tmp, feasibilityResultDistribution);
        parameters.getEventBus().register(tmp);

        final ProblemSaverDecorator.ProblemSaverInfo problemSaverInfo = ProblemSaverDecorator.ProblemSaverInfo.builder()
                .interference(parameters.getConstraintSet())
                .maxChannel(parameters.getMaxChannel())
                .build();
        final ProblemSaverDecorator problemSaverDecorator = new ProblemSaverDecorator(tmp, parameters.getProblemFolder(), problemSaverInfo);
        parameters.getEventBus().register(problemSaverDecorator);
        tmp = problemSaverDecorator;

        TimeTrackerFeasibilitySolverDecorator timeTrackingDecorator = new TimeTrackerFeasibilitySolverDecorator(tmp, simulatorWatch, simulatorCPU);
        tmp = timeTrackingDecorator;
        parameters.getEventBus().register(tmp);

        @Cleanup
        final IFeasibilitySolver solver = tmp;

        final long notParticipatingUS = stationDB.getStations().stream()
                .filter(s -> s.getNationality().equals(Nationality.US))
                .map(participation::getParticipation)
                .filter(p -> p.equals(Participation.EXITED_NOT_PARTICIPATING))
                .count();
        final long totalUS = stationDB.getStations().stream().filter(s -> s.getNationality().equals(Nationality.US)).count();
        log.info("There are {} non-participating US stations out of {} US stations", notParticipatingUS, totalUS);

        if (notParticipatingUS == totalUS) {
            log.warn("No one is participating in the auction! Ending");
            return;
        }

        final Set<IStationInfo> onAirStations = participation.getOnAirStations();
        log.info("Finding an initial assignment for the {} initially on air stations", onAirStations.size());
        for (Band band : ladder.getAirBands()) {
            final Set<IStationInfo> bandStations = ladder.getBandStations(band);
            if (bandStations.size() > 0) {
                final SimulatorResult initialFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(bandStations, band, ProblemType.INITIAL_PLACEMENT, null));
                Preconditions.checkState(SimulatorUtils.isFeasible(initialFeasibility), "Initial non-participating stations in %s do not have a feasible assignment! (Result was %s)", band, initialFeasibility.getSATFCResult().getResult());
                log.info("Found an initial assignment for the {} non-participating stations in band {}, {}", bandStations.size(), band, initialFeasibility.getSATFCResult().getWitnessAssignment());
                // This is a bit awkward (Should go through the ladder... but oh well)
                previousAssignmentHandler.updatePreviousAssignment(initialFeasibility.getSATFCResult().getWitnessAssignment());
            }
        }

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
                .baseClockPrice(openingPricesPerUnitVolume.get(Band.OFF))
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

}
