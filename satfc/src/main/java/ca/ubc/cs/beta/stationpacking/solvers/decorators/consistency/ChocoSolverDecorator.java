package ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.IntConstraintFactory;
import org.chocosolver.solver.constraints.nary.cnf.SatConstraint;
import org.chocosolver.solver.trace.Chatterbox;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.VariableFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 23/06/15.
 */
public class ChocoSolverDecorator extends ASolverDecorator {

    IStationManager stationManager;
    IConstraintManager constraintManager;

    /**
     * @param aSolver - decorated ISolver.
     */
    public ChocoSolverDecorator(ISolver aSolver) {
        super(aSolver);
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        Solver solver = new Solver();
        // Create all of the variables
        final Map<Station, IntVar> stationToVar = new HashMap<>(aInstance.getStations().size());
        aInstance.getStations().forEach(station -> {
            int[] domain = aInstance.getDomains().get(station).stream().mapToInt(Integer::intValue).toArray();
            VariableFactory.enumerated(Integer.toString(station.getID()), domain, solver);
        });
        aInstance.getStations().forEach(station -> {
            aInstance.getDomains().get(station).forEach(channel -> {
                constraintManager.getCOInterferingStations(station, channel).forEach(interferingStation -> {
                    // TODO:
                    IntConstraintFactory.arithm(stationToVar.get(station), "!=", stationToVar.get(interferingStation));                });
            });
        });
        solver.post();
        solver.findSolution();
        Chatterbox.printStatistics(solver);

        return super.solve(aInstance, aTerminationCriterion, aSeed);
    }
}
