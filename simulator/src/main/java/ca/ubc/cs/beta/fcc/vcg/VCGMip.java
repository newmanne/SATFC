package ca.ubc.cs.beta.fcc.vcg;

import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.fcc.simulator.MultiBandAuctioneer;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.matroid.encoder.MaxSatEncoder;
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
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearIntExpr;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math.util.MathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableList;

/**
 * Created by newmanne on 2016-05-19.
 */
public class VCGMip {

    private static Logger log;

    @UsageTextField(title = "VCG Parameters", description = "VCG Parameters")
    public static class VCGParameters extends AbstractOptions {

        @ParametersDelegate
        private MultiBandSimulatorParameters simulatorParameters = new MultiBandSimulatorParameters();

        @Parameter(names = "-VCG-PACKING")
        List<Integer> ids = new ArrayList<>();

        @Parameter(names = "-NOT-PARTICIPATING")
        List<Integer> notParticipating = new ArrayList<>();

        @Parameter(names = "-O", required = true)
        String outputFile;

        @Parameter(names = "-MIP-TYPE")
        MIPType mipType = MIPType.VCG;

        @Parameter(names = "-CPLEX-THREADS")
        int nThreads = Runtime.getRuntime().availableProcessors();

        // When to drop stations due to city requirement. After dropping stations with no valuations / domain in CT, or before?
        // Dropping after leads to fewer stations overall (since graph is less connected)
        @Parameter(names = "-CITY-DROP-AFTER")
        boolean cityDropAfter = true;


    }

    enum MIPType {
        VCG,
        SMALLEST_MAXIMAL
    }

    public static void main(String[] args) throws IloException, IOException {
        final VCGParameters q = new VCGParameters();
        JCommanderHelper.parseCheckingForHelpAndVersion(args, q);
        final MultiBandSimulatorParameters parameters = q.simulatorParameters;
        SATFCFacadeBuilder.initializeLogging(parameters.getFacadeParameters().getLogLevel(), parameters.getFacadeParameters().logFileName);
        JCommanderHelper.logCallString(args, VCGMip.class);
        log = LoggerFactory.getLogger(VCGMip.class);

        parameters.setUp();
        if (!q.cityDropAfter && parameters.getCity() != null) {
            new SimulatorParameters.CityAndLinks(parameters.getCity(), parameters.getNLinks(), parameters.getStationDB(), parameters.getConstraintManager()).function();
        }
        SimulatorUtils.assignValues(parameters);
        MultiBandAuctioneer.adjustCTSimple(parameters.getMaxChannel(), parameters.getStationDB());
        if (q.cityDropAfter && parameters.getCity() != null) {
            new SimulatorParameters.CityAndLinks(parameters.getCity(), parameters.getNLinks(), parameters.getStationDB(), parameters.getConstraintManager()).function();
        }
        final IStationDB.IModifiableStationDB stationDB = parameters.getStationDB();
        final Map<Integer, Set<Integer>> domains = stationDB.getStations().stream().collect(Collectors.toMap(IStationInfo::getId, IStationInfo::getDomain));
        log.info("Looking at {} stations", domains.size());
        log.info("Using max channel {}", parameters.getMaxChannel());
        final IConstraintManager constraintManager = parameters.getConstraintManager();
        final IMIPEncoder encoder;
        if (q.mipType.equals(MIPType.VCG)) {
            encoder = new VCGMIPMaker();
        } else if (q.mipType.equals(MIPType.SMALLEST_MAXIMAL)) {
            encoder = new SmallestMaximalCardinalityMIPMaker();
        } else {
            throw new IllegalStateException();
        }

        final Set<Integer> notParticipating = new HashSet<>(q.notParticipating);
        notParticipating.addAll(stationDB.getStations(Nationality.CA).stream().map(IStationInfo::getId).collect(Collectors.toSet()));
        final MIPMaker mipMaker = new MIPMaker(stationDB, parameters.getStationManager(), constraintManager, encoder);
        final MIPResult mipResult = mipMaker.solve(domains, notParticipating, parameters.getCutoff(), (int) parameters.getSeed(), q.nThreads, true, 0.);

        if (q.mipType.equals(MIPType.VCG)) {
            final double computedValue = mipResult.getAssignment().entrySet().stream()
                    .filter(e -> !notParticipating.contains(e.getKey()))
                    .mapToDouble(e -> stationDB.getStationById(e.getKey()).getValues().get(BandHelper.toBand(e.getValue())))
                    .sum();
            final double obj = mipResult.getObjectiveValue();
            if (computedValue != obj) {
                log.warn("Computed {} but obj was {} difference of {}", computedValue, obj, Math.abs(computedValue - obj));
            } else {
                log.info("Assignment matches up with objective value as expected");
            }
        }

        final String result = JSONUtils.toString(mipResult);
        FileUtils.writeStringToFile(new File(q.outputFile), result);
    }

    @Data
    @Builder
    public static class MIPResult {
        private Double objectiveValue;
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

    // TODO: check pairwise, might speed things up
    @Slf4j
    public static class SmallestMaximalCardinalityMIPMaker implements IMIPEncoder {

        @Override
        public void encode(Map<Integer, Set<Integer>> domains, Set<Integer> participating, Set<Integer> nonParticipating, IStationDB stationDB, Table<Integer, Integer, IloIntVar> varLookup, Map<IloIntVar, StationChannel> variablesDecoder, IConstraintManager constraintManager, IloCplex cplex) throws IloException {
            // Objective
            final IloLinearIntExpr objectiveSum = cplex.linearIntExpr();
            for (final Integer station : participating) {
                final IloIntVar[] domainVars = varLookup.row(station).values().stream().toArray(IloIntVar[]::new);
                final int[] values = new int[domainVars.length];
                Arrays.fill(values, 1);
                objectiveSum.addTerms(values, domainVars);
            }
            cplex.addMinimize(objectiveSum);

            // Greedy clauses
            final Map<Station, Set<Integer>> stationDomains = domains.entrySet().stream().collect(Collectors.toMap(e -> new Station(e.getKey()), Map.Entry::getValue));
            for (final int station : participating) {
                // Create the sum
                final IloLinearIntExpr domainSum = cplex.linearIntExpr();
                final IloIntVar[] domainVars = varLookup.row(station).values().stream().toArray(IloIntVar[]::new);
                final int[] values = new int[domainVars.length];
                Arrays.fill(values, 1);
                domainSum.addTerms(values, domainVars);

                for (int channel : domains.get(station)) {
                    final IloLinearIntExpr channelSpecificSum = cplex.linearIntExpr();
                    channelSpecificSum.add(domainSum);
                    for (StationChannel sc : MaxSatEncoder.getConstraintsForChannel(constraintManager, new Station(station), channel, stationDomains)) {
                        final IloIntVar interferingVar = varLookup.get(sc.getStation(), sc.getChannel());
                        channelSpecificSum.addTerm(1, interferingVar);
                    }
                    cplex.addGe(channelSpecificSum, 1);
                }
            }
        }
    }

    @Slf4j
    public static class VCGMIPMaker implements IMIPEncoder {

        @Override
        public void encode(Map<Integer, Set<Integer>> domains, Set<Integer> participating, Set<Integer> nonParticipating, IStationDB stationDB, Table<Integer, Integer, IloIntVar> varLookup, Map<IloIntVar, StationChannel> variablesDecoder, IConstraintManager constraintManager, IloCplex cplex) throws IloException {
            // Objective function
            final IloLinearNumExpr objectiveSum = cplex.linearNumExpr();
            for (final Integer station : participating) {
                final IloIntVar[] domainVars = varLookup.row(station).values().stream().toArray(IloIntVar[]::new);
                final double[] values = new double[domainVars.length];
                for (int i = 0; i < domainVars.length; i++) {
                    IloIntVar var = domainVars[i];
                    int chan = variablesDecoder.get(var).getChannel();
                    final Band band = BandHelper.toBand(chan);
                    final double value = stationDB.getStationById(station).getValues().get(band);
                    values[i] = value;
                }
                objectiveSum.addTerms(values, domainVars);
            }
            cplex.addMaximize(objectiveSum);
        }
    }

    public interface IMIPEncoder {

        void encode(Map<Integer, Set<Integer>> domains, Set<Integer> participating, Set<Integer> nonParticipating, IStationDB stationDB, Table<Integer, Integer, IloIntVar> varLookup, Map<IloIntVar, StationChannel> variablesDecoder, IConstraintManager constraintManager, IloCplex cplex) throws IloException;

    }

    @Slf4j
    // Not thread safe. Should be reusuable.
    public static class MIPMaker {

        protected final IStationDB stationDB;
        protected final IStationManager stationManager;
        protected final IConstraintManager constraintManager;
        private final IMIPEncoder encoder;
        protected IloCplex cplex;

        // Station, Channel -> Var
        protected Table<Integer, Integer, IloIntVar> varLookup;
        protected Map<IloIntVar, StationChannel> variablesDecoder;

        public MIPMaker(IStationDB stationDB, IStationManager stationManager, IConstraintManager constraintManager, IMIPEncoder encoder) throws IloException {
            this.stationDB = stationDB;
            this.stationManager = stationManager;
            this.constraintManager = constraintManager;
            this.encoder = encoder;
        }

        public MIPResult solve(Map<Integer, Set<Integer>> domains, double cutoff, long seed, int nThreads) throws IloException {
            return solve(domains, ImmutableSet.of(), cutoff, seed, nThreads, true, 0.);
        }

        public MIPResult solve(Map<Integer, Set<Integer>> domains, Set<Integer> nonParticipating, double cutoff, long seed, int nThreads, boolean writeToDisk, Double tol) throws IloException {
            this.varLookup = HashBasedTable.create();
            this.variablesDecoder = new HashMap<>();
            this.cplex = new IloCplex();

            Watch watch = Watch.constructAutoStartWatch();

            final Set<Integer> participating = Sets.difference(domains.keySet(), nonParticipating);
            log.info("{} / {} participating stations", participating.size(), domains.size());

            // Set up the x_{s,c} variables
            for (final Map.Entry<Integer, Set<Integer>> domainsEntry : domains.entrySet()) {
                final int station = domainsEntry.getKey();
                final List<Integer> domainList = domainsEntry.getValue().stream().sorted().collect(toImmutableList());
                Preconditions.checkState(!domainList.isEmpty(), "Station %s has no domain!", station);
                final IloIntVar[] domainVars = cplex.boolVarArray(domainList.size());
//                log.debug("{}", station);
                for (int i = 0; i < domainList.size(); i++) {
                    final int channel = domainList.get(i);
                    final IloIntVar var = domainVars[i];
                    var.setName(Integer.toString(station) + ":" + Integer.toString(channel));
                    varLookup.put(station, channel, var);
                    variablesDecoder.put(var, new StationChannel(station, channel));
                }
            }

            // Non participating stations get exactly 1 channel, participating get 0 or 1
            for (final Integer station : domains.keySet()) {
                final IloIntVar[] domainVars = varLookup.row(station).values().stream().toArray(IloIntVar[]::new);
                if (nonParticipating.contains(station)) {
                    // Must be placed on air
                    cplex.addEq(cplex.sum(domainVars), 1);
                } else {
                    // Optionally go on at most 1 channel
                    cplex.addLe(cplex.sum(domainVars), 1);
                }
            }

            // Add the interference constraints
            int nInterference = 0;
            for (Constraint constraint : constraintManager.getAllRelevantConstraints(domains.entrySet().stream().collect(Collectors.toMap(e -> new Station(e.getKey()), Map.Entry::getValue)))) {
                final IloIntVar var1 = varLookup.get(constraint.getSource().getID(), constraint.getSourceChannel());
                final IloIntVar var2 = varLookup.get(constraint.getTarget().getID(), constraint.getTargetChannel());
                cplex.addLe(cplex.sum(var1, var2), 1);
                nInterference++;
            }
            log.info("Added {} interference constraints", nInterference);

            // Do the rest of the encoding!
            encoder.encode(domains, participating, nonParticipating, stationDB, varLookup, variablesDecoder, constraintManager, cplex);

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
                if (tol != null) {
                    cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, tol);
                }
                cplex.setParam(IloCplex.Param.ClockType, 1); // CPU Time
                cplex.setParam(IloCplex.IntParam.Threads, nThreads);
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

            if (writeToDisk) {
                cplex.exportModel("model.lp");
            }

            //Gather output
            final SATResult satisfiability;
            final double runtime = watch.getElapsedTime();
            log.info("Runtime was {}", runtime);
            final Map<Integer, Integer> assignment;

            final IloCplex.Status status = cplex.getStatus();
            log.info("CPLEX status: {}", status);

            Double objValue = null;
            if (status.equals(IloCplex.Status.Optimal) || status.equals(IloCplex.Status.Feasible)) {
                objValue = cplex.getObjValue();
                log.info("CPLEX objective: {}", objValue);
            }

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
                if (!StationPackingUtils.weakVerify(stationManager, constraintManager, assignment)) {
                    throw new IllegalStateException("Could not verify assignment: " + assignment);
                }
                log.info("Verified!");
                log.debug("Assignment is {}", assignment);
                log.info("Assignment contains {} stations on air", assignment.size());
            }

            if (writeToDisk) {
                cplex.writeSolution("solution.lp");
            }

            final double cpuTime = cplex.getCplexTime();

            //Wrap up.
            cplex.end();
            return MIPResult.builder()
                    .assignment(assignment)
                    .objectiveValue(objValue)
                    .status(status)
                    .stations(domains.keySet())
                    .notParticipating(nonParticipating)
                    .cputime(cpuTime)
                    .walltime(watch.getElapsedTime())
                    .build();
        }

        private Map<Integer, Integer> getAssignment() throws IloException {
            double eps = cplex.getParam(IloCplex.DoubleParam.EpInt);
            final Map<Integer, Integer> assignment = new HashMap<>();
            for (Map.Entry<IloIntVar, StationChannel> entryDecoder : variablesDecoder.entrySet()) {
                final IloIntVar variable = entryDecoder.getKey();
                try {
                    log.debug("{} = {}", variable.getName(), cplex.getValue(variable));
                    if (MathUtils.equals(cplex.getValue(variable), 1, eps)) {
                        final StationChannel stationChannelPair = entryDecoder.getValue();
                        final Integer station = stationChannelPair.getStation();
                        final Integer channel = stationChannelPair.getChannel();
                        final Integer prevValue = assignment.put(station, channel);
                        Preconditions.checkState(prevValue == null, "%s was already assigned to %s and tried to assign again to %s!!!", station, prevValue, channel);
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
