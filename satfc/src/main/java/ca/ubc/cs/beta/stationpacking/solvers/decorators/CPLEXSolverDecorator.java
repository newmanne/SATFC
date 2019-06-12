package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Preconditions;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.cplex.IloCplex;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.math.util.MathUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by newmanne on 2016-06-27.
 */
@Slf4j
public class CPLEXSolverDecorator extends ASolverDecorator {

    private final MIPSaverDecorator.MIPEncoder encoder;
    private final boolean parameterized;
    private IloCplex.Aborter aborter;
    private final Lock lock = new ReentrantLock();


    /**
     * @param aSolver - decorated ISolver.
     */
    public CPLEXSolverDecorator(@NonNull ISolver aSolver,
                                @NonNull IConstraintManager constraintManager,
                                boolean parameterized) {
        super(aSolver);
        this.encoder = new MIPSaverDecorator.MIPEncoder(constraintManager);
        this.parameterized = parameterized;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Watch watch = Watch.constructAutoStartWatch();
        MIPSaverDecorator.MIPEncoderResult encode = encoder.encode(aInstance);
        final IloCplex cplex = encode.getCplex();
        try {
            aborter = new IloCplex.Aborter();
            cplex.use(aborter);
            cplex.setParam(IloCplex.DoubleParam.TimeLimit, aTerminationCriterion.getRemainingTime());
            cplex.setParam(IloCplex.LongParam.RandomSeed, (int) aSeed);
            cplex.setParam(IloCplex.IntParam.MIPEmphasis, IloCplex.MIPEmphasis.Feasibility);
            cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
//            cplex.setParam(IloCplex.Param.ClockType, 1); // CPU Time, default is wall
            cplex.setParam(IloCplex.IntParam.Threads, 1);


            if (this.parameterized) {
                // Use the best config we know about
                cplex.setParam(IloCplex.Param.Barrier.Algorithm, 1);
                cplex.setParam(IloCplex.Param.Barrier.Crossover, 1);
                cplex.setParam(IloCplex.Param.Barrier.Limits.Corrections, 16);
                cplex.setParam(IloCplex.Param.Barrier.Limits.Growth, 2043394461100.1316);
                cplex.setParam(IloCplex.Param.Barrier.Ordering, 3);
                cplex.setParam(IloCplex.Param.Barrier.StartAlg, 2);
                cplex.setParam(IloCplex.Param.Emphasis.Memory, false);
                cplex.setParam(IloCplex.Param.Emphasis.Numerical, false);
                cplex.setParam(IloCplex.Param.Feasopt.Mode, 1);
                cplex.setParam(IloCplex.Param.RootAlgorithm, 6);
                cplex.setParam(IloCplex.Param.MIP.Cuts.Cliques, 0);
                cplex.setParam(IloCplex.Param.MIP.Cuts.Covers, 3);
                cplex.setParam(IloCplex.Param.MIP.Cuts.Disjunctive, 0);
                cplex.setParam(IloCplex.Param.MIP.Cuts.FlowCovers, 1);
                cplex.setParam(IloCplex.Param.MIP.Cuts.Gomory, 0);
                cplex.setParam(IloCplex.Param.MIP.Cuts.GUBCovers, -1);
                cplex.setParam(IloCplex.Param.MIP.Cuts.Implied, 2);
                cplex.setParam(IloCplex.Param.MIP.Cuts.MCFCut, 1);
                cplex.setParam(IloCplex.Param.MIP.Cuts.MIRCut, 1);
                cplex.setParam(IloCplex.Param.MIP.Cuts.PathCut, 0);
                cplex.setParam(IloCplex.Param.MIP.Cuts.ZeroHalfCut, 0);
                cplex.setParam(IloCplex.Param.MIP.Limits.AggForCut, 3);
                cplex.setParam(IloCplex.Param.MIP.Limits.CutPasses, 64);
                cplex.setParam(IloCplex.Param.MIP.Limits.CutsFactor, 1.4882278332160317);
                cplex.setParam(IloCplex.Param.MIP.Limits.GomoryCand, 93);
                cplex.setParam(IloCplex.Param.MIP.Limits.GomoryPass, 64);
//                cplex.setParam(IloCplex.Param.MIP.Limits.SubMIPNodeLim, 271);
                cplex.setParam(IloCplex.Param.MIP.OrderType, 1);
                cplex.setParam(IloCplex.Param.MIP.Strategy.Backtrack, .99);
                cplex.setParam(IloCplex.Param.MIP.Strategy.BBInterval, 8);
                cplex.setParam(IloCplex.Param.MIP.Strategy.Branch, 1);
                cplex.setParam(IloCplex.Param.MIP.Strategy.Dive, 1);
                cplex.setParam(IloCplex.Param.MIP.Strategy.File, 0);
                cplex.setParam(IloCplex.Param.MIP.Strategy.FPHeur, 0);
                cplex.setParam(IloCplex.Param.MIP.Strategy.HeuristicFreq, 10);
                cplex.setParam(IloCplex.Param.MIP.Strategy.LBHeur, true);
                cplex.setParam(IloCplex.Param.MIP.Strategy.NodeSelect, 3);
                cplex.setParam(IloCplex.Param.MIP.Strategy.PresolveNode, -1);
                cplex.setParam(IloCplex.Param.MIP.Strategy.Probe, 2);
                cplex.setParam(IloCplex.Param.MIP.Strategy.RINSHeur, 10);
                cplex.setParam(IloCplex.Param.MIP.Strategy.Search, 2);
                // TODO: This parameter has two different names in the ineteractive optimzer!!!! This should correspond to startalgorithm
//                cplex.setParam(IloCplex.Param.RootAlgorithm, 1);
                cplex.setParam(IloCplex.Param.NodeAlgorithm, 1);
                cplex.setParam(IloCplex.Param.MIP.Strategy.VariableSelect, IloCplex.VariableSelect.MinInfeas);
                cplex.setParam(IloCplex.Param.Network.NetFind, 2);
                cplex.setParam(IloCplex.Param.Network.Pricing, 2);
                cplex.setParam(IloCplex.Param.Preprocessing.Aggregator, 16);
                cplex.setParam(IloCplex.Param.Preprocessing.BoundStrength, -1);
                cplex.setParam(IloCplex.Param.Preprocessing.CoeffReduce, 1);
                cplex.setParam(IloCplex.Param.Preprocessing.Dependency, 3);
                cplex.setParam(IloCplex.Param.Preprocessing.Dual, -1);
                cplex.setParam(IloCplex.Param.Preprocessing.Fill, 7);
                cplex.setParam(IloCplex.Param.Preprocessing.Linear, 0);
                cplex.setParam(IloCplex.Param.Preprocessing.NumPass, 0);
                cplex.setParam(IloCplex.Param.Preprocessing.Reduce, 1);
                cplex.setParam(IloCplex.Param.Preprocessing.Relax, 0);
                cplex.setParam(IloCplex.Param.Preprocessing.RepeatPresolve, 1);
                cplex.setParam(IloCplex.Param.Preprocessing.Symmetry, -1);

                cplex.setParam(IloCplex.Param.Read.Scale, -1);
                cplex.setParam(IloCplex.Param.Sifting.Algorithm, 0);
                cplex.setParam(IloCplex.Param.Simplex.Crash, -1);
                cplex.setParam(IloCplex.Param.Simplex.DGradient, 4);
                cplex.setParam(IloCplex.Param.Simplex.Limits.Perturbation, 0);
                cplex.setParam(IloCplex.Param.Simplex.Limits.Singularity, 39);
                cplex.setParam(IloCplex.Param.Simplex.Perturbation.Indicator, false);
                cplex.setParam(IloCplex.Param.Simplex.PGradient, 0);
                cplex.setParam(IloCplex.Param.Simplex.Pricing, 4);
                cplex.setParam(IloCplex.Param.Simplex.Refactor, 16);
                cplex.setParam(IloCplex.Param.Simplex.Tolerances.Markowitz, 0.0073286998524949125);
                cplex.setParam(IloCplex.Param.MIP.Strategy.Order, true);
            }

            cplex.solve();
            lock.lock();
            aborter = null;
            lock.unlock();
            final IloCplex.Status status = cplex.getStatus();
            if (status.equals(IloCplex.Status.Infeasible)) {
                return SolverResult.createNonSATResult(SATResult.UNSAT, watch.getElapsedTime(), SolverResult.SolvedBy.CPLEX);
            } else if (status.equals(IloCplex.Status.Optimal) || status.equals(IloCplex.Status.Feasible)) {
                Map<Integer, Integer> assignment = getAssignment(cplex, encode.getDecoder());
                return new SolverResult(SATResult.SAT, watch.getElapsedTime(), StationPackingUtils.channelToStationFromStationToChannel(assignment), SolverResult.SolvedBy.CPLEX);
            }
        } catch (IloException e) {
            e.printStackTrace();
            log.error("CPLEX could not solve the MIP.", e);
            throw new IllegalStateException("CPLEX could not solve the MIP (" + e.getMessage() + ").");
        } finally {
            cplex.end();
        }
        return SolverResult.relabelTime(fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed), watch.getElapsedTime());
    }

    private Map<Integer, Integer> getAssignment(IloCplex cplex, Map<IloIntVar, MIPSaverDecorator.StationChannel> variablesDecoder) throws IloException {
        double eps = cplex.getParam(IloCplex.DoubleParam.EpInt);
        final Map<Integer, Integer> assignment = new HashMap<>();
        for (Map.Entry<IloIntVar, MIPSaverDecorator.StationChannel> entryDecoder : variablesDecoder.entrySet()) {
            final IloIntVar variable = entryDecoder.getKey();
            try {
                log.debug("{} = {}", variable.getName(), cplex.getValue(variable));
                if (MathUtils.equals(cplex.getValue(variable), 1, eps)) {
                    final MIPSaverDecorator.StationChannel stationChannelPair = entryDecoder.getValue();
                    final Station station = stationChannelPair.getStation();
                    final Integer channel = stationChannelPair.getChannel();
                    final Integer prevValue = assignment.put(station.getID(), channel);
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

    @Override
    public void interrupt() {
        // TODO: Note that this is NOT GOING TO WORK PROPERLY IF YOU USE THE ParallelNoWaitSolverComposite because you might already be on the next problem and abort the wrong thing!
        lock.lock();
        if (aborter != null) {
            aborter.abort();
        }
        lock.unlock();
        super.interrupt();
    }
}
