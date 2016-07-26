package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.cplex.IloCplex;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.NullOutputStream;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Created by newmanne on 2016-06-27.
 */
@Slf4j
public class MIPSaverDecorator extends ASolverDecorator {

    private final IConstraintManager constraintManager;

    @Data
    public static class StationChannel {
        private final Station station;
        private final int channel;
    }

    /**
     * @param aSolver - decorated ISolver.
     */
    public MIPSaverDecorator(@NonNull ISolver aSolver,
                             @NonNull IConstraintManager constraintManager) {
        super(aSolver);
        this.constraintManager = constraintManager;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        try {
            final IloCplex cplex = new IloCplex();

            // Make all the variables for station channel pairings
            final Map<IloIntVar, StationChannel> variablesDecoder = new HashMap<>();
            final Map<Station, Map<Integer, IloIntVar>> variablesMap = new HashMap<>();

            for (final Entry<Station, Set<Integer>> entry : aInstance.getDomains().entrySet()) {
                final Station station = entry.getKey();
                final Set<Integer> domain = entry.getValue();
                final Map<Integer, IloIntVar> stationVariablesMap = new HashMap<>();
                variablesMap.put(station, stationVariablesMap);
                final IloIntVar[] domainVars = cplex.boolVarArray(domain.size());
                Iterator<Integer> domainIterator = domain.iterator();
                int i = 0;
                while (domainIterator.hasNext()) {
                    final int channel = domainIterator.next();
                    final IloIntVar domainVar = domainVars[i];
                    domainVar.setName(station + ":" + Integer.toString(channel));
                    stationVariablesMap.put(channel, domainVar);
                    variablesDecoder.put(domainVar, new StationChannel(station, channel));
                    i++;
                }
                // Domain constraint
                cplex.addEq(cplex.sum(domainVars), 1);
            }

            // Add interference
            for (Constraint constraint : constraintManager.getAllRelevantConstraints(aInstance.getDomains())) {
                final IloIntVar var1 = variablesMap.get(constraint.getSource()).get(constraint.getSourceChannel());
                final IloIntVar var2 = variablesMap.get(constraint.getTarget()).get(constraint.getTargetChannel());
                cplex.addLe(cplex.sum(var1, var2), 1);
            }

            //Add dummy objective function.
            cplex.addMaximize();
            cplex.setOut(new NullOutputStream());

            // Write to file!
            final String filename = aInstance.getName() + ".lp";
            log.info("Saving to {}", filename);
            cplex.exportModel(filename);

            cplex.end();
        } catch (IloException e) {
            throw new RuntimeException("Couldn't save MIP", e);
        }

        return super.solve(aInstance, aTerminationCriterion, aSeed);
    }


}
