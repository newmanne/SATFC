package ca.ubc.cs.beta.fcc.simulator.clearingtargetoptimization;

import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.vcg.VCGMip;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import com.google.common.collect.Table;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearIntExpr;
import ilog.cplex.IloCplex;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Created by newmanne on 2018-04-09.
 */
@Slf4j
public class ClearingTargetOptimizationMIP implements VCGMip.IMIPEncoder {
    // Objective: Lexicographic preferences. 1) Minimize the number of impairing stations 2) Minimize the impairing population

    private final Set<Integer> impairingChannels;

    public ClearingTargetOptimizationMIP(@NonNull Set<Integer> impairingChannels) {
        this.impairingChannels = impairingChannels;
    }

    @Override
    public void encode(Map<Integer, Set<Integer>> domains, Set<Integer> participating, Set<Integer> nonParticipating, IStationDB stationDB, Table<Integer, Integer, IloIntVar> varLookup, Map<IloIntVar, VCGMip.StationChannel> variablesDecoder, IConstraintManager constraintManager, IloCplex cplex) throws IloException {
        final IloLinearIntExpr objectiveSum = cplex.linearIntExpr();
        for (final Integer station : nonParticipating) {
            final List<IloIntVar> impairingVariables = getImpairingChannelVarsForStation(varLookup, station);
            final IloIntVar[] domainVars = impairingVariables.stream().toArray(IloIntVar[]::new);
            final int[] values = new int[domainVars.length];
            Arrays.fill(values, 1);
            objectiveSum.addTerms(values, domainVars);
        }
        cplex.addMinimize(objectiveSum);
    }

    private List<IloIntVar> getImpairingChannelVarsForStation(Table<Integer, Integer, IloIntVar> varLookup, Integer station) {
        final List<IloIntVar> impairingVariables = new ArrayList<>();
        for (int channel : impairingChannels) {
            final IloIntVar iloIntVar = varLookup.get(station, channel);
            if (iloIntVar != null) {
                impairingVariables.add(iloIntVar);
            }
        }
        return impairingVariables;
    }


    public static class ClearingTargetOptimizationMIPPhaseTwo implements VCGMip.IMIPEncoder {

        private final int nImpairingStations;
        private final Set<Integer> impairingChannels;

        public ClearingTargetOptimizationMIPPhaseTwo(final int nImpairingStations, @NonNull Set<Integer> impairingChannels) {
            this.nImpairingStations = nImpairingStations;
            this.impairingChannels = impairingChannels;
        }


        @Override
        public void encode(Map<Integer, Set<Integer>> domains, Set<Integer> participating, Set<Integer> nonParticipating, IStationDB stationDB, Table<Integer, Integer, IloIntVar> varLookup, Map<IloIntVar, VCGMip.StationChannel> variablesDecoder, IConstraintManager constraintManager, IloCplex cplex) throws IloException {
            final IloLinearIntExpr objectiveSum = cplex.linearIntExpr();
            final List<IloIntVar> allDomainVars = new ArrayList<>();
            for (final Integer station : nonParticipating) {
                final IStationInfo stationObject = stationDB.getStationById(station);
                final int population = stationObject.getPopulation();
                final List<IloIntVar> impairingVariables = new ArrayList<>();
                for (int channel : impairingChannels) {
                    final IloIntVar iloIntVar = varLookup.get(station, channel);
                    if (iloIntVar != null) {
                        impairingVariables.add(iloIntVar);
                    }
                }
                final IloIntVar[] domainVars = impairingVariables.stream().toArray(IloIntVar[]::new);
                final int[] values = new int[domainVars.length];
                Arrays.fill(values, population);
                objectiveSum.addTerms(values, domainVars);
                allDomainVars.addAll(impairingVariables);
            }
            cplex.addLe(cplex.sum(allDomainVars.stream().toArray(IloIntVar[]::new)), nImpairingStations);
            cplex.addMinimize(objectiveSum);
        }
    }

}


