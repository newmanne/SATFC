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

/**
 * Created by newmanne on 2016-06-27.
 */
@Slf4j
public class CPLEXSolverDecorator extends ASolverDecorator {

    private final MIPSaverDecorator.MIPEncoder encoder;

    /**
     * @param aSolver - decorated ISolver.
     */
    public CPLEXSolverDecorator(@NonNull ISolver aSolver,
                                @NonNull IConstraintManager constraintManager) {
        super(aSolver);
        this.encoder = new MIPSaverDecorator.MIPEncoder(constraintManager);
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final Watch watch = Watch.constructAutoStartWatch();
        MIPSaverDecorator.MIPEncoderResult encode = encoder.encode(aInstance);
        final IloCplex cplex = encode.getCplex();
        try {
            cplex.setParam(IloCplex.DoubleParam.TimeLimit, aTerminationCriterion.getRemainingTime());
            cplex.setParam(IloCplex.LongParam.RandomSeed, (int) aSeed);
            cplex.setParam(IloCplex.IntParam.MIPEmphasis, IloCplex.MIPEmphasis.Feasibility);
            cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
            cplex.setParameterSet(IloCplex.ParameterSet.);
//            cplex.setParam(IloCplex.Param.ClockType, 1); // CPU Time
            cplex.setParam(IloCplex.IntParam.Threads, 1);
            cplex.solve();
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


}
