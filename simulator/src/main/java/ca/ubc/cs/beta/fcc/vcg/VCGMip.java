package ca.ubc.cs.beta.fcc.vcg;

import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.station.CSVStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import ilog.concert.IloException;
import ilog.concert.IloIntExpr;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.NullOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-05-19.
 */
public class VCGMip {

    private static Logger log;

    public static void main(String[] args) throws IloException, IOException {
        final SimulatorParameters parameters = new SimulatorParameters();
        JCommanderHelper.parseCheckingForHelpAndVersion(args, parameters);
        // TODO: probably want to override the default name...
        SATFCFacadeBuilder.initializeLogging(parameters.getFacadeParameters().getLogLevel(), parameters.getFacadeParameters().logFileName);
        JCommanderHelper.logCallString(args, VCGMip.class);
        log = LoggerFactory.getLogger(VCGMip.class);

        parameters.setUp();

        final StationDB stationDB = new CSVStationDB(parameters.getInfoFile());
        final Map<Integer, Set<Integer>> domains = parameters.getProblemGenerator().createProblem(SimulatorUtils.toID(stationDB.getStations())).getDomains();

        final IConstraintManager constraintManager = parameters.getConstraintManager();
        final MIPMaker mipMaker = new MIPMaker(stationDB, constraintManager, domains);

        mipMaker.makeMip();
        mipMaker.solve(parameters.getCutoff(), (int) parameters.getSeed());
    }

    @Data
    public static class StationChannel {
        private final int station;
        private final int channel;
    }

    @Slf4j
    public static class MIPMaker {

        private final StationDB stationDB;
        private final IConstraintManager constraintManager;
        private final Map<Integer, Set<Integer>> domains;
        private IloCplex cplex;

        Map<Integer, Map<Integer, IloIntVar>> variablesMap;
        Map<IloIntVar, StationChannel> variablesDecoder;

        public MIPMaker(StationDB stationDB, IConstraintManager constraintManager, Map<Integer, Set<Integer>> domains) throws IloException {
            this.stationDB = stationDB;
            this.constraintManager = constraintManager;
            this.domains = domains;
            cplex = new IloCplex();
        }

        public void makeMip() throws IloException {
            Watch watch = Watch.constructAutoStartWatch();
            // 1) Create the variables
            // TODO: this creation is really inefficient, but might not be slow in practice
            variablesMap = new HashMap<>();
            variablesDecoder = new HashMap<>();
            final IloLinearNumExpr sum = cplex.linearNumExpr();

            for (final Map.Entry<Integer, Set<Integer>> domainsEntry : domains.entrySet()) {
                final int station = domainsEntry.getKey();
                final Map<Integer, IloIntVar> stationVariablesMap = new HashMap<>();
                final List<Integer> domainList = new ArrayList<>(domainsEntry.getValue());
                final IloIntVar[] domainVars = cplex.boolVarArray(domainList.size());
                for (int i = 0; i < domainList.size(); i++) {
                    final int channel = domainList.get(i);
                    final IloIntVar var = domainVars[i];
                    var.setName(Integer.toString(station) + ":" + Integer.toString(channel));
                    stationVariablesMap.put(channel, var);

                    sum.addTerm(stationDB.getStationById(station).getValue(), var);
                }
                // Constraint: Station on 0 or 1 channels
                final IloIntExpr channelSum = cplex.sum(domainVars);
                cplex.addLe(channelSum, 1);
                variablesMap.put(station, stationVariablesMap);
            }
            // Interference
            for (Constraint constraint : constraintManager.getAllRelevantConstraints(domains.entrySet().stream().collect(Collectors.toMap(e -> new Station(e.getKey()), Map.Entry::getValue)))) {
                final IloIntVar var1 = variablesMap.get(constraint.getSource().getID()).get(constraint.getSourceChannel());
                final IloIntVar var2 = variablesMap.get(constraint.getTarget().getID()).get(constraint.getTargetChannel());
                cplex.le(cplex.sum(var1, var2), 1);
            }
            // Objective!
            cplex.addMaximize(sum);
            log.info("Encoding MIP took {} s.", watch.getElapsedTime());
            log.info("MIP has {} variables.", cplex.getNcols());
            log.info("MIP has {} constraints.", cplex.getNrows());
        }

        public void solve(double cutoff, int seed) throws IloException {

            // This turns off CPLEX logging.
            cplex.setOut(new NullOutputStream());

            //Set CPLEX's parameters.
            try {
                cplex.setParam(IloCplex.DoubleParam.TimeLimit, cutoff);
                cplex.setParam(IloCplex.LongParam.RandomSeed, seed);
            } catch (IloException e) {
                log.error("Could not set CPLEX's parameters to the desired values", e);
                throw new IllegalStateException("Could not set CPLEX's parameters to the desired values (" + e.getMessage() + ").");
            }

            Watch watch = Watch.constructAutoStartWatch();
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
                log.info("Assignment is {}", assignment);
            }

            //Wrap up.
            cplex.end();
        }

        private Map<Integer, Integer> getAssignment() {
            final Map<Integer, Integer> assignment = new HashMap<>();
            for (Map.Entry<IloIntVar, StationChannel> entryDecoder : variablesDecoder.entrySet()) {
                final IloIntVar variable = entryDecoder.getKey();
                try {
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
