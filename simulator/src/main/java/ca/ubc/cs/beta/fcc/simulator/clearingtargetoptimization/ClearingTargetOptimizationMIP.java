package ca.ubc.cs.beta.fcc.simulator.clearingtargetoptimization;

import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.vcg.VCGMip;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.Table;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearIntExpr;
import ilog.cplex.IloCplex;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * Created by newmanne on 2018-04-09.
 */
@Slf4j
public class ClearingTargetOptimizationMIP implements VCGMip.IMIPEncoder {
    // Objective: Lexicographic preferences. 1) Minimize the number of impairing stations 2) Minimize the impairing population

    public final static int IMPAIRING_CHANNEL = StationPackingUtils.UHFmax + 1;

    private final Integer nStations;
    private final boolean secondPhase;

    public ClearingTargetOptimizationMIP() {
        this.secondPhase = false;
        nStations = null;
    }

    public ClearingTargetOptimizationMIP(int nStations) {
        this.nStations = nStations;
        this.secondPhase = true;
    }

    @Override
    public void encode(Map<Integer, Set<Integer>> domains, Set<Integer> participating, Set<Integer> nonParticipating, IStationDB stationDB, Table<Integer, Integer, IloIntVar> varLookup, Map<IloIntVar, VCGMip.StationChannel> variablesDecoder, IConstraintManager constraintManager, IloCplex cplex) throws IloException {
        // Objective value: minimize the sum of stations that get placed on an impairing channel!
        Set<Integer> possibleToImpair = domains.entrySet().stream().filter(e -> e.getValue().contains(IMPAIRING_CHANNEL)).map(Map.Entry::getKey).collect(toSet());


        final IloLinearIntExpr objectiveSum = cplex.linearIntExpr();
        final IloIntVar[] variables = new IloIntVar[possibleToImpair.size()];
        final int[] values = new int[possibleToImpair.size()];
        int i = 0;
        for (final Integer station : possibleToImpair) {
            final IloIntVar var = varLookup.get(station, IMPAIRING_CHANNEL);
            Preconditions.checkNotNull(var);
            variables[i] = var;
            if (this.secondPhase) {
                values[i] = stationDB.getStationById(station).getPopulation();
            } else {
                values[i] = 1;
            }
            i++;
        }
        // If this is the second phase, add an extra constraint generated for the first phase
        if (this.secondPhase) {
            cplex.addLe(cplex.sum(variables), this.nStations);
        }
        objectiveSum.addTerms(values, variables);
        cplex.addMinimize(objectiveSum);
    }


}

