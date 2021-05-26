//package ca.ubc.cs.beta.fcc.vcg;
//
//import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
//import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
//import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
//import ca.ubc.cs.beta.fcc.simulator.MultiBandAuctioneer;
//import ca.ubc.cs.beta.fcc.simulator.clearingtargetoptimization.ClearingTargetOptimizationMIP;
//import ca.ubc.cs.beta.fcc.simulator.clearingtargetoptimization.WorstOffenderMIP;
//import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameters;
//import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
//import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
//import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
//import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
//import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
//import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
//import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
//import ca.ubc.cs.beta.fcc.simulator.utils.Band;
//import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
//import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
//import ca.ubc.cs.beta.fcc.vcg.VCGMip.VCGParameters;
//import ca.ubc.cs.beta.matroid.encoder.MaxSatEncoder;
//import ca.ubc.cs.beta.stationpacking.base.Station;
//import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
//import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
//import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
//import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
//import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
//import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
//import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
//import ca.ubc.cs.beta.stationpacking.utils.LoggingOutputStream;
//import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
//import ca.ubc.cs.beta.stationpacking.utils.Watch;
//import com.beust.jcommander.Parameter;
//import com.beust.jcommander.ParametersDelegate;
//import com.fasterxml.jackson.databind.annotation.JsonSerialize;
//import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
//import com.google.common.base.Preconditions;
//import com.google.common.collect.*;
//import ilog.concert.IloException;
//import ilog.concert.IloIntVar;
//import ilog.concert.IloLinearIntExpr;
//import ilog.concert.IloLinearNumExpr;
//import ilog.cplex.IloCplex;
//import lombok.Builder;
//import lombok.Data;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.csv.CSVRecord;
//import org.apache.commons.io.FileUtils;
//import org.apache.commons.math.util.MathUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.*;
//import java.util.stream.Collectors;
//
//import static ca.ubc.cs.beta.fcc.simulator.MultiBandAuctioneer.setOpeningPrices;
//import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableList;
//import static java.util.stream.Collectors.toSet;
//
///**
// * Created by newmanne on 2016-05-19.
// */
//public class WorstOffender {
//
//    private static Logger log;
//
//    @UsageTextField(title = "", description = "")
//    public static class WorstOffenderParameters extends AbstractOptions {
//
//        @ParametersDelegate
//        private MultiBandSimulatorParameters simulatorParameters = new MultiBandSimulatorParameters();
//
//        @ParametersDelegate
//        private VCGParameters q = new VCGParameters();
//
//
//    }
//
//    public static void main(String[] args) throws IloException, IOException {
//        final WorstOffenderParameters z = new WorstOffenderParameters();
//        JCommanderHelper.parseCheckingForHelpAndVersion(args, z);
//        final MultiBandSimulatorParameters parameters = z.simulatorParameters;
//        final VCGParameters q = z.q;
//        SATFCFacadeBuilder.initializeLogging(parameters.getFacadeParameters().getLogLevel(), parameters.getFacadeParameters().logFileName);
//        JCommanderHelper.logCallString(args, WorstOffender.class);
//        log = LoggerFactory.getLogger(WorstOffender.class);
//
//        parameters.setUp();
//        final Map<IStationInfo, Integer> counts = new HashMap<>();
//        for (IStationInfo s: parameters.getStationDB().getStations()) {
//            counts.put(s, 0);
//        }
//        SimulatorUtils.adjustCTSimple(parameters.getMaxChannel(), parameters.getStationDB());
//        final IStationDB.IModifiableStationDB stationDB = parameters.getStationDB();
//        final IConstraintManager constraintManager = parameters.getConstraintManager();
//
//        for (int i = 0; i < 1000; i++ ) {
//            SimulatorUtils.assignValues(parameters);
//            // Figure out participation
//            final MultiBandAuctioneer.OpeningPrices setOpeningPrices = setOpeningPrices(parameters);
//            final IPrices<Long> actualPrices = setOpeningPrices.getActualPrices();
//            ParticipationRecord participation = new ParticipationRecord(stationDB, parameters.getParticipationDecider(actualPrices));
//            final Set<Integer> notParticipating = participation.getMatching(Participation.INACTIVE).stream().map(IStationInfo::getId).collect(Collectors.toSet());
//
//            final Map<Integer, Set<Integer>> domains = participation.getMatching(Participation.INACTIVE).stream().collect(Collectors.toMap(IStationInfo::getId, IStationInfo::getDomain));
//            final Map<Station, Set<Integer>> domainsAsStation = participation.getMatching(Participation.INACTIVE).stream().collect(Collectors.toMap(IStationInfo::toSATFCStation, IStationInfo::getDomain));
//
//            // Split into CCs
//            ConstraintGrouper constraintGrouper = new ConstraintGrouper();
//            Set<Set<Station>> connectedComponents = ConstraintGrouper.group(ConstraintGrouper.getConstraintGraph(domainsAsStation, parameters.getConstraintManager()));
//
//            for (Set<Station> component : connectedComponents) {
//                boolean isSAT;
//                if (isSAT) {
//                    continue;
//                }
//
//                final VCGMip.IMIPEncoder encoder = new WorstOffenderMIP(new HashSet<>());
//                final VCGMip.MIPMaker mipMaker = new VCGMip.MIPMaker(stationDB, parameters.getStationManager(), constraintManager, encoder);
//                final VCGMip.MIPResult mipResult = mipMaker.solve(domains, notParticipating, parameters.getCutoff(), (int) parameters.getSeed(), parameters.getParallelism(), true, 0., new HashMap<>(), true);
//
//                // get stations on impairing channel
//
//                // add to set of constraints
//
//                // resolve until infeasible
//
//            }
//
//
//        }
//
//
//
//        final String result = JSONUtils.toString(mipResult);
//        FileUtils.writeStringToFile(new File(q.outputFile), result);
//    }
//
//}
