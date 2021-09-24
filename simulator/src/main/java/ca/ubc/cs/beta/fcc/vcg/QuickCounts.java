package ca.ubc.cs.beta.fcc.vcg;

import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.fcc.simulator.MultiBandAuctioneer;
import ca.ubc.cs.beta.fcc.simulator.clearingtargetoptimization.ClearingTargetOptimizationMIP;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.fcc.vcg.VCGMip.VCGParameters;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacade;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import ilog.concert.IloException;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ca.ubc.cs.beta.fcc.simulator.MultiBandAuctioneer.abortIfNecessary;
import static ca.ubc.cs.beta.fcc.simulator.MultiBandAuctioneer.setOpeningPrices;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/**
 * Created by newmanne on 2016-05-19.
 */
public class QuickCounts {

    private static Logger log;

    public static void main(String[] args) throws IloException, IOException {
//        final VCGParameters z = new VCGParameters();
//        JCommanderHelper.parseCheckingForHelpAndVersion(args, z);
//        final MultiBandSimulatorParameters parameters = z.simulatorParameters;
//        SATFCFacadeBuilder.initializeLogging(parameters.getFacadeParameters().getLogLevel(), parameters.getFacadeParameters().logFileName);
//        JCommanderHelper.logCallString(args, QuickCounts.class);
//        log = LoggerFactory.getLogger(QuickCounts.class);
//
//        SATFCFacade solver = new SATFCFacadeBuilder().build();
//
//        parameters.setUp();
//        if (parameters.getStationsToUseFile() != null) {
//            Iterable<CSVRecord> csvRecords = SimulatorUtils.readCSV(parameters.getStationsToUseFile());
//            for (CSVRecord record : csvRecords) {
//                int facID = Integer.parseInt(record.get("FacID"));
//                parameters.getStationDB().removeStation(facID);
//                log.info("Removing station {}", facID);
//            }
//        }
//
//        // Set up CT for 84 MHz
//        SimulatorUtils.adjustCTSimple(parameters.getMaxChannel(), parameters.getStationDB());
//
//        final IStationDB.IModifiableStationDB stationDB = parameters.getStationDB();
//        final VCGMip.IMIPEncoder clearingTargetOptimizationMIP = new ClearingTargetOptimizationMIP();
//        final VCGMip.MIPMaker mipMaker;
//        mipMaker = new VCGMip.MIPMaker(stationDB, parameters.getStationManager(), parameters.getConstraintManager(), clearingTargetOptimizationMIP);
//
//        // Set up counts
//        final Map<Integer, Integer> counts = new HashMap<>();
//        for (IStationInfo s : parameters.getStationDB().getStations()) {
//            counts.put(s.getId(), 0);
//        }
//
//        int goodCount = 0;
//
//        for (int j = 0; j < 1000; j++) {
//            log.info("Good count is {} out of {} trials", goodCount, j);
//
//            log.info("" + j);
//            SimulatorUtils.assignValues(parameters);
//            // Figure out participation
//            final MultiBandAuctioneer.OpeningPrices setOpeningPrices = setOpeningPrices(parameters);
//            final IPrices<Long> actualPrices = setOpeningPrices.getActualPrices();
//            ParticipationRecord participation = new ParticipationRecord(stationDB, parameters.getParticipationDecider(actualPrices));
//            final Set<IStationInfo> notParticipating = participation.getMatching(Participation.INACTIVE);
//
//            final Map<Integer, Set<Integer>> domains = notParticipating
//                    .stream().collect(toMap(
//                            IStationInfo::getId,
//                            s -> s.getDomain(s.getHomeBand())
//                    ));
//
//            SATFCResult satfcResult = solver.solve(domains, new HashMap<>(), 60, 1, parameters.getStationInfoFolder());
//
//            if (satfcResult.getResult().equals(SATResult.SAT)) {
//                log.info("No impairing stations");
//                goodCount++;
//            } else {
//                final Map<Integer, Set<Integer>> impairingDomains = notParticipating
//                        .stream().collect(toMap(
//                                IStationInfo::getId,
//                                s -> Sets.union(s.getDomain(s.getHomeBand()), ImmutableSet.of(ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL)))
//                        );
//
//                // SAT CC's can be excluded for speed
//                final Map<Station, Set<Integer>> domainsAsStation = participation.getMatching(Participation.INACTIVE).stream().collect(Collectors.toMap(IStationInfo::toSATFCStation, IStationInfo::getDomain));
//                Set<Set<Station>> connectedComponents = ConstraintGrouper.group(ConstraintGrouper.getConstraintGraph(domainsAsStation, parameters.getConstraintManager()));
//                for (Set<Station> component : connectedComponents) {
//                    Map<Integer, Set<Integer>> reducedDomains = component.stream().map(Station::getID).collect(toMap(s -> s, domains::get));
//                    SATFCResult result = solver.solve(reducedDomains, new HashMap<>(), 15, 1, parameters.getStationInfoFolder());
//                    if (result.getResult().equals(SATResult.SAT)) {
//                        log.info("Removing {} stations", component.size());
//                        component.stream().map(Station::getID).forEach(impairingDomains::remove);
//                    } else {
//                        log.info("Component was {}", result.getResult());
//                    }
//                }
//
//                if (impairingDomains.isEmpty()) {
////                log.info("SATFC killed, skipping");
//                    continue;
//                }
//
//                final VCGMip.MIPResult phaseOneResult = mipMaker.solve(impairingDomains, impairingDomains.keySet(), parameters.getMipCutoff(), parameters.getSeed(), parameters.getParallelism(), false, null, new HashMap<>(), false);
//                abortIfNecessary(phaseOneResult);
//                final int nImpairingStations = (int) Math.round(phaseOneResult.getObjectiveValue());
//                log.info("{} stations will be impairing", nImpairingStations);
//                Map<Integer, Integer> assignment;
//
//                if (nImpairingStations == 0) {
//                    // No point in going to phase 2, we didn't find anything better than we already knew about
//                    log.debug("Skipping phase 2 of clearing target optimization");
//                    assignment = phaseOneResult.getAssignment();
//                } else {
//                    log.info("Now finding best set (by minimizing pop of impairing stations)");
//                    final ClearingTargetOptimizationMIP clearingTargetOptimizationMIPPhaseTwo = new ClearingTargetOptimizationMIP(nImpairingStations);
//                    final VCGMip.MIPMaker mipMakerPhaseTwo = new VCGMip.MIPMaker(stationDB, parameters.getStationManager(), parameters.getConstraintManager(), clearingTargetOptimizationMIPPhaseTwo);
//                    final VCGMip.MIPResult phaseTwoResult = mipMakerPhaseTwo.solve(impairingDomains, impairingDomains.keySet(), parameters.getMipCutoff(), parameters.getSeed(), parameters.getParallelism(), false, null, phaseOneResult.getAssignment(), false);
//                    abortIfNecessary(phaseTwoResult);
//                    Preconditions.checkState(phaseTwoResult.getAssignment().values().stream().filter(c -> c == ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL).count() <= nImpairingStations, "Phase 2 result has more impairing stations than phase 1 said was necessary");
//                    assignment = phaseTwoResult.getAssignment();
//                }
//
//                // get stations on impairing channel
//                final Set<IStationInfo> impairingStations = assignment.entrySet().stream().filter(e -> e.getValue() == ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL)
//                        .map(e -> stationDB.getStationById(e.getKey()))
//                        .collect(toSet());
//
//                for (IStationInfo s : impairingStations) {
//                    counts.putIfAbsent(s.getId(), 0);
//                    counts.put(s.getId(), counts.get(s.getId()) + 1);
//                }

//            }
//
//        }
//
//        log.info("Good count is {}", goodCount);
//
//        log.info("" + JSONUtils.toString(counts));
//        log.info("Max value is " + counts.values().stream().mapToInt(s -> s).max().getAsInt());
//
//
    }

}
