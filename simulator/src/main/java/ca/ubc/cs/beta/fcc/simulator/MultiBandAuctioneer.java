package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.FeasibilityStateHolder;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IFeasibilityStateHolder;
import ca.ubc.cs.beta.fcc.simulator.ladder.IModifiableLadder;
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
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilityVerifier;
import ca.ubc.cs.beta.fcc.simulator.solver.decorator.FeasibilityResultDistributionDecorator;
import ca.ubc.cs.beta.fcc.simulator.solver.decorator.TimeTrackerFeasibilitySolverDecorator;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.FeasibilityVerifier;
import ca.ubc.cs.beta.fcc.simulator.state.LadderAuctionState;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.time.TimeTracker;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.SimulatorUnconstrainedCheckerImpl;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.fcc.simulator.vacancy.IVacancyCalculator;
import ca.ubc.cs.beta.fcc.simulator.vacancy.ParallelVacancyCalculator;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
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
        SATFCFacadeBuilder.initializeLogging(parameters.getFacadeParameters().getLogLevel(), parameters.getFacadeParameters().logFileName);
        JCommanderHelper.logCallString(args, Simulator.class);
        log = LoggerFactory.getLogger(MultiBandAuctioneer.class);
        parameters.setUp();

        log.info("Building solver");

        final TimeTracker timeTracker = new TimeTracker();
        final FeasibilityResultDistributionDecorator.FeasibilityResultDistribution feasibilityResultDistribution = new FeasibilityResultDistributionDecorator.FeasibilityResultDistribution();
        IFeasibilitySolver tmp = parameters.createSolver();
        tmp = new TimeTrackerFeasibilitySolverDecorator(tmp, timeTracker);
        tmp = new FeasibilityResultDistributionDecorator(tmp, feasibilityResultDistribution);
        final IFeasibilitySolver solver = tmp;
        // TODO: want a Greedy solver decorator to speed things up

        final IPreviousAssignmentHandler previousAssignmentHandler = new SimplePreviousAssignmentHandler();

        final IModifiableLadder ladder = new SimpleLadder(Arrays.asList(Band.values()));
        final IFeasibilityStateHolder problemMaker = new FeasibilityStateHolder(previousAssignmentHandler, ladder, parameters.createProblemSpecGenerator());

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

        final long notParticipatingUS = stationDB.getStations().stream()
                .filter(s -> s.getNationality().equals(Nationality.US))
                .map(participation::getParticipation)
                .filter(p -> p.equals(Participation.EXITED_NOT_PARTICIPATING))
                .count();
        final long totalUS = stationDB.getStations().stream().filter(s -> s.getNationality().equals(Nationality.US)).count();
        log.info("There are {} non-participating US stations out of {} US stations", notParticipatingUS, totalUS);
        final Set<IStationInfo> onAirStations = participation.getOnAirStations();
        log.info("Finding an initial assignment for the {} initially on air stations", onAirStations.size());
        final SATFCResult initialFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(onAirStations, ImmutableSet.copyOf(ladder.getAirBands())));
        Preconditions.checkState(SimulatorUtils.isFeasible(initialFeasibility), "Initial non-participating stations do not have a feasible assignment! (Result was %s)", initialFeasibility.getResult());
        log.info("Found an initial assignment for the non-participating stations");
        previousAssignmentHandler.updatePreviousAssignment(initialFeasibility.getWitnessAssignment());


        final Map<IStationInfo, Double> initialCompensations = new HashMap<>();
        for (IStationInfo s : ladder.getStations()) {
            if (Participation.NON_ZERO_PRICES.contains(participation.getParticipation(s))) {
                initialCompensations.put(s, actualPrices.getPrice(s, ladder.getStationBand(s)));
            } else {
                initialCompensations.put(s, 0.);
            }
        }

        LadderAuctionState state = new LadderAuctionState(
            benchmarkPrices,
            participation,
            0,
            previousAssignmentHandler.getPreviousAssignment(),
            ladder,
            initialCompensations,
            openingPricesPerUnitVolume.get(Band.OFF)
        );

        final IFeasibilityVerifier feasibilityVerifier = new FeasibilityVerifier(parameters.getConstraintManager(), parameters.getStationManager());
//        final IVacancyCalculator vacancyCalculator = new ParallelVacancyCalculator(
//                participation,
//                feasibilityVerifier,
//                parameters.getConstraintManager(),
//                parameters.getVacFloor(),
//                Runtime.getRuntime().availableProcessors()
//        );
        final IVacancyCalculator vacancyCalculator = new ParallelVacancyCalculator.SequentialVacancyCalculator(
                feasibilityVerifier,
                participation,
                parameters.getConstraintManager(),
                parameters.getVacFloor()
        );

        final MultiBandSimulator simulator = new MultiBandSimulator(
                MultiBandSimulatorParameter
                        .builder()
                        .parameters(parameters.getLadderAuctionParameter())
                        .previousAssignmentHandler(previousAssignmentHandler)
                        .problemMaker(problemMaker)
                        .vacancyCalculator(vacancyCalculator)
                        .solver(solver)
                        .unconstrainedChecker(new SimulatorUnconstrainedCheckerImpl(parameters.getConstraintManager(), participation))
                        .pricesFactory(PricesImpl::new)
                        .build()
        );

        log.info("Starting simulation!");
        while (true) {
            final LadderAuctionState nextState = simulator.executeRound(state);
            // If, after processing the bids from a round, every participating station has either exited or become provisionally winning, the stage ends
            if (state.getParticipation().getMatching(Participation.INACTIVE).equals(state.getLadder().getStations())) {
                log.info("All stations have exited or are provisional winners. Ending simulation");
                break;
            }
            // TODO: write out state to file here
            state = nextState;
        }

//        log.info("Final state:" + System.lineSeparator() + "{}", state);

        solver.close();
        timeTracker.report();
        log.info("Finished. Goodbye :)");
    }

}
