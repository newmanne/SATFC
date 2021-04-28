package ca.ubc.cs.beta.fcc.simulator.clearingtargetoptimization;

import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.vcg.VCGMip;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearIntExpr;
import ilog.cplex.IloCplex;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * Created by newmanne on 2018-04-09.
 */
@Slf4j
public class ClearingTargetOptimizationMIP_2 implements VCGMip.IMIPEncoder {
    // Objective: Minimize the impairing VALUE

    public ClearingTargetOptimizationMIP_2() {
    }

    @Override
    public void encode(Map<Integer, Set<Integer>> domains, Set<Integer> participating, Set<Integer> nonParticipating, IStationDB stationDB, Table<Integer, Integer, IloIntVar> varLookup, Map<IloIntVar, VCGMip.StationChannel> variablesDecoder, IConstraintManager constraintManager, IloCplex cplex) throws IloException {
        // Objective value: minimize the sum of stations that get placed on an impairing channel!
        Set<Integer> possibleToImpair = domains.entrySet().stream().filter(e -> e.getValue().contains(ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL)).map(Map.Entry::getKey).collect(toSet());

        final IloLinearIntExpr objectiveSum = cplex.linearIntExpr();
        final IloIntVar[] variables = new IloIntVar[possibleToImpair.size()];
        final int[] values = new int[possibleToImpair.size()];
        int i = 0;
        for (final Integer station : possibleToImpair) {
            final IloIntVar var = varLookup.get(station, ClearingTargetOptimizationMIP.IMPAIRING_CHANNEL);
            Preconditions.checkNotNull(var);
            variables[i] = var;
            long value = stationDB.getStationById(station).getValue();
            value /= 1000;
            if (value > Integer.MAX_VALUE) {
                throw new IllegalStateException("Too big value: " + value);
            }
            values[i] = (int) value;
            i++;
        }
        objectiveSum.addTerms(values, variables);
        cplex.addMinimize(objectiveSum);
    }

    @Override
    public void setParams(IloCplex cplex) throws IloException {
        cplex.setParam(IloCplex.IntParam.MIPEmphasis, IloCplex.MIPEmphasis.Balanced);
    }
}

