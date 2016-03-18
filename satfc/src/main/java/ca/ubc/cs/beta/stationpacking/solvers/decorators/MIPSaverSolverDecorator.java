/**
 * Copyright 2015, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 *
 * This file is part of SATFC.
 *
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml.EncodingType;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATEncoder;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.RedisUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.google.common.base.Joiner;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.cplex.IloCplex;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.util.Pair;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Solver decorator that saves CNFs on solve query.
 *
 * @author afrechet
 */
public class MIPSaverSolverDecorator extends ASolverDecorator {

    private final IConstraintManager fConstraintManager;
    private EncodingType encodingType;
    private final String mipDir;

    public MIPSaverSolverDecorator(@NonNull ISolver aSolver,
                                   @NonNull String mipDir,
                                   @NonNull IConstraintManager aConstraintManager,
                                   @NonNull EncodingType encodingType) {
        super(aSolver);
        this.encodingType = encodingType;
        fConstraintManager = aConstraintManager;
        this.mipDir = mipDir;
    }

    private Pair<Map<Station,Map<Integer,IloIntVar>>,Map<IloIntVar,Pair<Station,Integer>>> addVariables(IloCplex aMIP, StationPackingInstance aInstance) throws IloException
    {
        final Map<Station,Map<Integer,IloIntVar>> variablesMap = new HashMap<>();
        final Map<IloIntVar,Pair<Station,Integer>> variablesDecoder = new HashMap<>();

        final Map<Station,Set<Integer>> domains = aInstance.getDomains();

        for(final Map.Entry<Station,Set<Integer>> domainsEntry : domains.entrySet())
        {
            final Station station = domainsEntry.getKey();
            final Set<Integer> domain = domainsEntry.getValue();

            final Map<Integer,IloIntVar> stationVariablesMap = new HashMap<>();
            for(final Integer channel : domain)
            {
                final IloIntVar variable = aMIP.boolVar(Integer.toString(station.getID())+":"+Integer.toString(channel));

                stationVariablesMap.put(channel, variable);
                variablesDecoder.put(variable, new Pair<>(station, channel));
            }
            variablesMap.put(station, Collections.unmodifiableMap(stationVariablesMap));
        }

        return new Pair<>(Collections.unmodifiableMap(variablesMap), Collections.unmodifiableMap(variablesDecoder));
    }

    /**
     * Adds all the base constraints to the given MIP using the given variables.
     * @param aMIP - a MIP to modify.
     * @param aVariables - a map taking station to channel to variable for which base constraints are necessary.
     * @throws IloException
     */
    private void addBaseConstraints(IloCplex aMIP, Map<Station,Map<Integer,IloIntVar>> aVariables) throws IloException
    {
        for(final Station station : aVariables.keySet())
        {
            final Collection<IloIntVar> stationVariables = aVariables.get(station).values();
            final IloIntVar[] stationVariablesArray = stationVariables.toArray(new IloIntVar[stationVariables.size()]);

            // x_{s,c} + x_{s,c'} \leq 1
            for(int i=0;i<stationVariablesArray.length;i++)
            {
                for(int j=i+1;j<stationVariablesArray.length;j++)
                {
                    aMIP.addLe(aMIP.sum(stationVariablesArray[i],stationVariablesArray[j]), 1);
                }
            }

            if (encodingType.equals(EncodingType.DIRECT)) {
                // \sum_{c \in D(s)} x_{s,c} = 1
                aMIP.addEq(aMIP.sum(stationVariablesArray), 1);
            }
        }
    }

    /**
     * Adds all the interference constraints to the given MIP using the given variables.
     * @param aMIP - a MIP to modify.
     * @param aVariables - a map taking station to channel to variable for which base constraints are necessary.
     * @throws IloException
     */
    private void addInterferenceConstraints(IloCplex aMIP, Map<Station,Map<Integer,IloIntVar>> aVariables, IConstraintManager aConstraintManager, Map<Station, Set<Integer>> domains) throws IloException
    {
        for (Constraint constraint : aConstraintManager.getAllRelevantConstraints(domains)) {
            final IloIntVar sourceVariable = aVariables.get(constraint.getSource()).get(constraint.getSourceChannel());
            final IloIntVar targetVariable = aVariables.get(constraint.getTarget()).get(constraint.getTargetChannel());
            // x_{s,c} + x_{s',c'} \leq 1
            aMIP.addLe(aMIP.sum(sourceVariable, targetVariable), 1);
        }
    }

    /**
     * Adds all the necessary binary variables to the given MIP, and return a map of variables added.
     * @param aMIP - a MIP to modify.
     * @param aInstance - a station packing instance to get variables for.
     * @return an unmodifiable map of station to channel to variable.
     * @throws IloException
     */
    public Pair<IloCplex,Map<IloIntVar,Pair<Station,Integer>>> encodeMIP(StationPackingInstance aInstance, IConstraintManager aConstraintManager) throws IloException
    {
        IloCplex mip = new IloCplex();

        //Add constraints.
        Pair<Map<Station,Map<Integer,IloIntVar>>,Map<IloIntVar,Pair<Station,Integer>>> variablesMaps = addVariables(mip, aInstance);
        Map<Station,Map<Integer,IloIntVar>> variables = variablesMaps.getFirst();
        Map<IloIntVar,Pair<Station,Integer>> decoder = variablesMaps.getSecond();
        addBaseConstraints(mip,variables);
        addInterferenceConstraints(mip, variables, aConstraintManager, aInstance.getDomains());

        //Add dummy objective function.
        mip.addMaximize();

        return new Pair<>(mip, decoder);
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        Watch watch = Watch.constructAutoStartWatch();
        Pair<IloCplex,Map<IloIntVar,Pair<Station,Integer>>> encoding = null;
        try {
            encoding = encodeMIP(aInstance, fConstraintManager);
            final IloCplex mip = encoding.getFirst();
            mip.exportModel(mipDir + File.separator + aInstance.getName() + ".lp");
        } catch (IloException e) {
            throw new RuntimeException(e);
        }

        return super.solve(aInstance, aTerminationCriterion, aSeed);
    }


}
