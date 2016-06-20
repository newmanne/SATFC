package ca.ubc.cs.beta.fcc.vcg;

import ca.ubc.cs.beta.aeatk.logging.LogLevel;
import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.station.CSVStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.LoggingOutputStream;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.google.common.collect.ImmutableSet;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;
import lombok.Data;
import lombok.experimental.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-05-19.
 */
public class VCGMip {

    private static Logger log;

    @UsageTextField(title = "VCG Parameters", description = "VCG Parameters")
    public static class VCGParameters extends AbstractOptions {

        @ParametersDelegate
        private SimulatorParameters simulatorParameters = new SimulatorParameters();

        @Parameter(names = "-VCG-PACKING")
        List<Integer> ids = new ArrayList<>();

        @Parameter(names = "-NOT-PARTICIPATING")
        List<Integer> notParticipating = new ArrayList<>();

        @Parameter(names = "-O", required = true)
        String outputFile;

    }

    public static void main(String[] args) throws IloException, IOException {
        final VCGParameters q = new VCGParameters();
        JCommanderHelper.parseCheckingForHelpAndVersion(args, q);
        // TODO: probably want to override the default name...
        final SimulatorParameters parameters = q.simulatorParameters;
        SATFCFacadeBuilder.initializeLogging(parameters.getFacadeParameters().getLogLevel(), parameters.getFacadeParameters().logFileName);
        JCommanderHelper.logCallString(args, VCGMip.class);
        log = LoggerFactory.getLogger(VCGMip.class);

        parameters.setUp();

        final StationDB stationDB = parameters.getStationDB();
        final Set<Integer> use = new HashSet<>(q.ids);
        final Map<Integer, Set<Integer>> domains = parameters.getProblemGenerator().createProblem(use).getDomains();
        log.info("Looking at {} stations", domains.size());
        log.info("Using max channel {}", parameters.getMaxChannel());
        final IConstraintManager constraintManager = parameters.getConstraintManager();

        final MIPMaker mipMaker = new MIPMaker(stationDB, parameters.getStationManager(), constraintManager);
        final MIPResult mipResult = mipMaker.solve(domains, new HashSet<>(q.notParticipating), parameters.getCutoff(), (int) parameters.getSeed());

        final double computedValue = mipResult.getAssignment().keySet().stream().mapToDouble(s -> stationDB.getStationById(s).getValue()).sum();
        final double obj = mipResult.getObjectiveValue();
        if (computedValue != obj) {
            log.warn("Computed {} but obj was {} difference of {}", computedValue, obj, Math.abs(computedValue - obj));
        } else {
            log.info("Assignment matches up with objective value as expected");
        }


        final String result = JSONUtils.toString(mipResult);
        FileUtils.writeStringToFile(new File(q.outputFile), result);

//        final List<List<Object>> records = new ArrayList<>();
//        if (mipResult.getStatus().equals(IloCplex.Status.Optimal)) {
//            for (Integer s : domains.keySet()) {
//                final StationInfo station = stationDB.getStationById(s);
//                final MIPResult stationResult = mipMaker.solve(domains, ImmutableSet.of(s), parameters.getCutoff(), parameters.getSeed());
//                double price = stationResult.getObjectiveValue() - (mipResult.getObjectiveValue() - (mipResult.getAssignment().containsKey(s) ? stationDB.getStationById(s).getValue() : 0));
//                boolean assigned = mipResult.getAssignment().containsKey(s);
//                if (assigned) {
//                    final String logString = String.format("Station %d was assigned to a channel. It will pay %f and values it's station at %f, for a surplus of %f", s, price, station.getValue(), station.getValue() - price);
//                    log.info(logString);
//                } else {
//                    final String logString = String.format("Station %d was not assigned a channel. It will be bought out for %f and values it's station at %f for a surplus of %f", s, -price, station.getValue(), -price - station.getValue());
//                    log.info(logString);
//                }
//                records.add(Arrays.asList(s, mipResult.getAssignment().containsKey(s) ? mipResult.getAssignment().get(s) : -1 ,price, station.getValue()));
//            }
//        }
//        SimulatorUtils.toCSV("output.csv", Arrays.asList("FacID", "Channel", "Price", "Value"), records);
    }

    @Data
    @Builder
    public static class MIPResult {
        private double objectiveValue;
        private Map<Integer, Integer> assignment;
        @JsonSerialize(using = ToStringSerializer.class)
        private IloCplex.Status status;
        private Set<Integer> stations;
        private Set<Integer> notParticipating;
        private double walltime;
        private double cputime;
    }

    @Data
    public static class StationChannel {
        private final int station;
        private final int channel;
    }

    @Slf4j
    public static class MIPMaker {

        private final StationDB stationDB;
        private final IStationManager stationManager;
        private final IConstraintManager constraintManager;
        private IloCplex cplex;

        Map<Integer, Map<Integer, IloIntVar>> variablesMap;
        Map<IloIntVar, StationChannel> variablesDecoder;

        public MIPMaker(StationDB stationDB, IStationManager stationManager, IConstraintManager constraintManager) throws IloException {
            this.stationDB = stationDB;
            this.stationManager = stationManager;
            this.constraintManager = constraintManager;
        }

        public MIPResult solve(Map<Integer, Set<Integer>> domains, double cutoff, long seed) throws IloException {
            return solve(domains, ImmutableSet.of(), cutoff, seed);
        }

        public MIPResult solve(Map<Integer, Set<Integer>> domains, Set<Integer> nonParticipating, double cutoff, long seed) throws IloException {
            Watch watch = Watch.constructAutoStartWatch();
            cplex = new IloCplex();
            // 1) Create the variables
            // TODO: this creation is really inefficient, but might not be slow in practice
            variablesMap = new HashMap<>();
            variablesDecoder = new HashMap<>();
            final IloLinearNumExpr sum = cplex.linearNumExpr();

            for (final Map.Entry<Integer, Set<Integer>> domainsEntry : domains.entrySet()) {
                final int station = domainsEntry.getKey();
                final List<Integer> domainList = new ArrayList<>(domainsEntry.getValue());
                final Map<Integer, IloIntVar> stationVariablesMap = new HashMap<>();
                final IloIntVar[] domainVars = cplex.boolVarArray(domainList.size());
                final double value = stationDB.getStationById(station).getValue();
                for (int i = 0; i < domainList.size(); i++) {
                    final int channel = domainList.get(i);
                    final IloIntVar var = domainVars[i];
                    var.setName(Integer.toString(station) + ":" + Integer.toString(channel));
                    stationVariablesMap.put(channel, var);
                    variablesDecoder.put(var, new StationChannel(station, channel));
                    // You can't contribute to value function if you aren't participating
                    if (!nonParticipating.contains(station)) {
                        sum.addTerm(value, var);
                    }
                }
                // Constraint: Station on 0 or 1 channels (binding 1 if not participating)
                if (nonParticipating.contains(station)) {
                    cplex.addEq(cplex.sum(domainVars), 1);
                } else {
                    cplex.addLe(cplex.sum(domainVars), 1);
                }
                variablesMap.put(station, stationVariablesMap);
            }
            // Interference
            int nInterference = 0;
            for (Constraint constraint : constraintManager.getAllRelevantConstraints(domains.entrySet().stream().collect(Collectors.toMap(e -> new Station(e.getKey()), Map.Entry::getValue)))) {
                final IloIntVar var1 = variablesMap.get(constraint.getSource().getID()).get(constraint.getSourceChannel());
                final IloIntVar var2 = variablesMap.get(constraint.getTarget().getID()).get(constraint.getTargetChannel());
                cplex.addLe(cplex.sum(var1, var2), 1);
                nInterference++;
            }
            log.info("Added {} interference constraints", nInterference);
            // Objective!
            cplex.addMaximize(sum);
            log.info("Encoding MIP took {} s.", watch.getElapsedTime());
            log.info("MIP has {} variables.", cplex.getNcols());
            log.info("MIP has {} constraints.", cplex.getNrows());

            // This turns off CPLEX logging.
            cplex.setOut(new LoggingOutputStream(LoggerFactory.getLogger("CPLEX"), LoggingOutputStream.LogLevel.INFO));

            //Set CPLEX's parameters.
            try {
                cplex.setParam(IloCplex.DoubleParam.TimeLimit, cutoff);
                cplex.setParam(IloCplex.LongParam.RandomSeed, (int) seed);
                cplex.setParam(IloCplex.IntParam.MIPEmphasis, IloCplex.MIPEmphasis.Optimality);
                cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
                cplex.setParam(IloCplex.Param.ClockType, 1); // CPU Time
            } catch (IloException e) {
                log.error("Could not set CPLEX's parameters to the desired values", e);
                throw new IllegalStateException("Could not set CPLEX's parameters to the desired values (" + e.getMessage() + ").");
            }

            watch.reset();
            watch.start();
            //Solve the MIP.
            final boolean feasible;
            try {
                feasible = cplex.solve();
            } catch (IloException e) {
                e.printStackTrace();
                log.error("CPLEX could not solve the MIP.", e);
                throw new IllegalStateException("CPLEX could not solve the MIP (" + e.getMessage() + ").");
            }

            //Gather output
            final SATResult satisfiability;
            final double runtime = watch.getElapsedTime();
            log.info("Runtime was {}", runtime);
            final Map<Integer, Integer> assignment;

            final IloCplex.Status status = cplex.getStatus();
            final double objValue = cplex.getObjValue();
            log.info("CPLEX status: {}. Objective: {}", status, objValue);

            if (status.equals(IloCplex.Status.Optimal)) {
                if (feasible) {
                    satisfiability = SATResult.SAT;
                    assignment = getAssignment();
                } else {
                    satisfiability = SATResult.UNSAT;
                    assignment = null;
                }
            } else if (status.equals(IloCplex.Status.Feasible)) {
                satisfiability = SATResult.SAT;
                //Parse the assignment.
                assignment = getAssignment();
            } else if (status.equals(IloCplex.Status.Infeasible)) {
                satisfiability = SATResult.UNSAT;
                assignment = null;
            } else if (status.equals(IloCplex.Status.Unknown)) {
                satisfiability = SATResult.TIMEOUT;
                assignment = null;
            } else {
                log.error("CPLEX has a bad post-execution status.");
                log.error(status.toString());
                satisfiability = SATResult.CRASHED;
                assignment = null;
            }

            log.info("Satisfiability is {}", satisfiability);
            if (assignment != null) {
                log.info("Verifying solution");
                StationPackingUtils.weakVerify(stationManager, constraintManager, assignment);
                log.info("Verified!");
                log.info("Assignment is {}", assignment);
                log.info("Assignment contains {} stations on air", assignment.size());
            }

            cplex.exportModel("model.lp");
            cplex.writeSolution("solution.lp");

            //Wrap up.
            cplex.end();
            return MIPResult.builder()
                    .assignment(assignment)
                    .objectiveValue(objValue)
                    .status(status)
                    .stations(domains.keySet())
                    .notParticipating(nonParticipating)
                    .cputime(cplex.getCplexTime())
                    .walltime(watch.getElapsedTime())
                    .build();
        }


        private Map<Integer, Integer> getAssignment() {
            final Map<Integer, Integer> assignment = new HashMap<>();
            for (Map.Entry<IloIntVar, StationChannel> entryDecoder : variablesDecoder.entrySet()) {
                final IloIntVar variable = entryDecoder.getKey();
                try {
                    log.info("{} = {}", variable.getName(), cplex.getValue(variable));
                    if (cplex.getValue(variable) == 1) {
                        final StationChannel stationChannelPair = entryDecoder.getValue();
                        final Integer station = stationChannelPair.getStation();
                        final Integer channel = stationChannelPair.getChannel();
                        assignment.put(station, channel);
                    }
                } catch (IloException e) {
                    e.printStackTrace();
                    log.error("Could not get MIP value assignment for variable " + variable + ".", e);
                    throw new IllegalStateException("Could not get MIP value assignment for variable " + variable + " (" + e.getMessage() + ").");
                }
            }
            return assignment;
        }

    }

}
