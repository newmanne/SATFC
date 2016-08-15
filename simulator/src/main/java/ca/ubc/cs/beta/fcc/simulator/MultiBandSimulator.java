package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.IStationOrderer;
import ca.ubc.cs.beta.fcc.simulator.ladder.IModifiableLadder;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPricesFactory;
import ca.ubc.cs.beta.fcc.simulator.prices.Prices;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.state.AuctionState;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.stepreductions.StepReductionCoefficientCalculator;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.fcc.simulator.vacancy.IVacancyCalculator;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Created by newmanne on 2016-08-02.
 */
@Slf4j
public class MultiBandSimulator {

    // TODO: save  / load state
    // TODO: solver API and how to leverage parallellism / caching
    // TODO: generate the proper constraint graphs for vacacancy to use

    private final IVacancyCalculator vacancyCalculator;
    private final IFeasibilitySolver solver;
    private final StepReductionCoefficientCalculator stepReductionCoefficientCalculator;

    private final IStationOrderer stationOrderer;
    private final LadderAuctionParameters parameters;

    private final IPricesFactory pricesFactory;

    @Data
    public static class LadderAuctionParameters {

        public LadderAuctionParameters() {
            r1 = 0.05;
            r2 = 0.01;
            VAC_FLOOR = 0.5;
            openingBenchmarkPrices = null;
        }

        private final double VAC_FLOOR;
        private final double r1;
        private final double r2;
        private final Map<Band, Double> openingBenchmarkPrices;

    }

    private AuctionState state;

    public MultiBandSimulator(LadderAuctionParameters parameters) {
    	this.parameters = parameters;
    	// TODO:
    	this.pricesFactory = null;
    	this.solver = null;
    	this.stationOrderer = null;
    	this.vacancyCalculator = null;
        stepReductionCoefficientCalculator = new StepReductionCoefficientCalculator(parameters.getOpeningBenchmarkPrices());
    }

    public static double benchmarkToActualPrice(IStationInfo station, Band band, Prices benchmarkPrices) {
        final double benchmarkHome = benchmarkPrices.getPrice(station, station.getHomeBand());
        final double nonVolumeWeightedActual = max(0, min(benchmarkPrices.getPrice(station, Band.OFF), benchmarkPrices.getPrice(station, band) - benchmarkHome));
        return station.getVolume() * nonVolumeWeightedActual;
    }

    public void executeRound() {
        final int round = state.getRound() + 1;

        final IModifiableLadder ladder = state.getLadder();

        final Prices benchmarkPrices = state.getBenchmarkPrices();
        final Prices newBechmarkPrices = pricesFactory.create();
        final ParticipationRecord participation = state.getParticipation();
        final Prices actualPrices = pricesFactory.create();
        final Map<Integer, Integer> assignment = new HashMap<>(state.getAssignment());

        log.debug("Retrieving active stations...");
        final Set<IStationInfo> activeStations = participation.getActiveStations();

        log.debug("Computing vacancies...");
        final ImmutableTable<IStationInfo, Band, Double> vacancies = vacancyCalculator.computeVacancies(activeStations, ladder, assignment);

        log.debug("Calculating reduction coefficients...");
        final ImmutableTable<IStationInfo, Band, Double> reductionCoefficients = stepReductionCoefficientCalculator.computeStepReductionCoefficients(vacancies);

        log.debug("Calculating new benchmark prices...");
        for (IStationInfo station : activeStations) {

            // Either 5% of previous value or 1% of starting value
            final double decrement = Math.max(parameters.getR1() * benchmarkPrices.getPrice(station, Band.OFF), parameters.getR2() * parameters.getOpeningBenchmarkPrices().get(Band.OFF));

            for (Band band : ladder.getPossibleMoves(station)) {
                // If this station were a "comparable" UHF station, the prices for all of the moves...
                final double benchmarkValue = benchmarkPrices.getPrice(station, band) - reductionCoefficients.get(station, band) * decrement;
                newBechmarkPrices.setPrice(station, band, benchmarkValue);
                actualPrices.setPrice(station, band, benchmarkToActualPrice(station, band, newBechmarkPrices));
            }

        }

        // BIDDING PHASE
        final ImmutableList<IStationInfo> stationsToQuery = stationOrderer.getQueryOrder(activeStations, benchmarkPrices, ladder);
        int qOrder = 1;
        // TODO: do I need a while true / break? Does query order change per round?
        for (IStationInfo station : stationsToQuery) {
            log.debug("------------------------------------------------------------------------");
            final Band currentBand = ladder.getStationBand(station);
            final Band homeBand = station.getHomeBand();
            log.debug("[{}] Querying {}-homed station {} currently on {}...", qOrder, homeBand, station, currentBand);

            // Frozen -> station is infeasible in its home band
            log.debug("Checking if frozen...", station);
            final SATFCResult frozenFeasibility = null;// TODO: solver.getFeasibilityBlocking(station, station.getHomeBand());
            final boolean frozen = !SimulatorUtils.isFeasible(frozenFeasibility);
            log.debug(" ... {} ({}).", frozen, frozenFeasibility.getResult());

            if (!frozen) {
                log.debug("Finding feasibility for permissible bands...");
                final ImmutableMap<Band, SATFCResult> bandToFeasibility = null;//ladder.getPossibleMoves(station)
//                        .stream()
//                        .collect(Collectors.toMap(band -> band, band -> solver.getFeasibilityBlocking(station, band)))
//                        .entrySet()
//                        .stream()
//                        .filter(entry -> SimulatorUtils.isFeasible(entry.getValue()))
//                        .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

                log.debug("Feasible permissible bands: {}", bandToFeasibility.keySet());
                final ImmutableMap<Band, Double> offers = actualPrices.getPrices(station, bandToFeasibility.keySet());
                log.debug("Prices are {}:", offers);
                final Band preferredBand = station.queryPreferredBand(offers);
                log.debug("Chose band: {}.", preferredBand);

                if (!preferredBand.equals(currentBand)) {
                    log.debug("Moving station and adjusting feasible assignment.");
                    ladder.moveStation(station, preferredBand);
                    assignment.putAll(bandToFeasibility.get(preferredBand).getWitnessAssignment());
                }
            } else {
                log.debug("Station is frozen - no new bands to offer");
                if (homeBand.equals(Band.UHF)) {
                    participation.setParticipation(station, Participation.FROZEN_PROVISIONALLY_WINNING);
                } else if (homeBand.equals(Band.HVHF)) {
                    // If there are UHF stations that are not provisionally winning, then an HVHF station cannot be a provisional winner, since the UHF station might exit
                }


                participation.setParticipation(station, Participation.FROZEN_CURRENTLY_INFEASIBLE);
            }
            qOrder++;
        }

        // BID STATUS UPDATING

        // TODO: calculate
        final Map<IStationInfo, Boolean> stationToFeasibleInHomeBand = null;
        for (Map.Entry<IStationInfo, Boolean> entry : stationToFeasibleInHomeBand.entrySet()) {
            IStationInfo station = entry.getKey();
            boolean feasibleInHomeBand = entry.getValue();
            final Participation bidStatus = participation.getParticipation(station);

            // Not feasible in pre-auction band and was not provisionally winning
            if (!feasibleInHomeBand && !bidStatus.equals(Participation.FROZEN_PROVISIONALLY_WINNING)) {
                // Is it provisionally winning?
                if (station.getHomeBand().equals(Band.UHF)) {
                    participation.setParticipation(station, Participation.FROZEN_PROVISIONALLY_WINNING);
                } else if (station.getHomeBand().equals(Band.HVHF)) {

                } else if (station.getHomeBand().equals(Band.LVHF)) {

                }
            } else {
                // for every active station feasible in home band check if is not needed
                // TODO: write the checker!
                if (false) {
                    participation.setParticipation(station, Participation.EXITED_NOT_NEEDED);
                }
            }

            if (!bidStatus.equals(Participation.FROZEN_PROVISIONALLY_WINNING) && !Participation.EXITED.contains(bidStatus)) {
                participation.setParticipation(station, feasibleInHomeBand ? Participation.BIDDING: Participation.FROZEN_CURRENTLY_INFEASIBLE);
            }
        }

        //Update termination criterion.
//        if (numActiveStations == 0)
//
//        {
//            log.info("All stations are inactive (either frozen or exited).");
//            fTerminated = true;
//            return;
//        }
//
//        log.info("There were " + numActiveStations + " active stations that round");
//
    }

}