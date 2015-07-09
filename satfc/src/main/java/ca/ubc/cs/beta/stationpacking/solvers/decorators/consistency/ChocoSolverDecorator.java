package ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.tuple.Pair;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.ICF;
import org.chocosolver.solver.constraints.extension.Tuples;
import org.chocosolver.solver.search.limits.BacktrackCounter;
import org.chocosolver.solver.search.loop.monitors.SearchMonitorFactory;
import org.chocosolver.solver.search.solution.Solution;
import org.chocosolver.solver.search.strategy.ISF;
import org.chocosolver.solver.search.strategy.selectors.VariableSelectorWithTies;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMin;
import org.chocosolver.solver.search.strategy.selectors.variables.FirstFail;
import org.chocosolver.solver.search.strategy.selectors.variables.Random;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.VariableFactory;
import org.python.google.common.base.Preconditions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 23/06/15.
 */
@Slf4j
public class ChocoSolverDecorator extends ASolverDecorator {

    final IStationManager stationManager;
    final IConstraintManager constraintManager;

    /**
     * @param aSolver - decorated ISolver.
     * @param stationManager
     * @param constraintManager
     */
    public ChocoSolverDecorator(ISolver aSolver, IStationManager stationManager, IConstraintManager constraintManager) {
        super(aSolver);
        this.stationManager = stationManager;
        this.constraintManager = constraintManager;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        Watch watch = Watch.constructAutoStartWatch();
        final Solver solver = new Solver();
        // Create all of the variables
        final Map<Station, IntVar> stationToVar = new HashMap<>(aInstance.getStations().size());
        aInstance.getStations().forEach(station -> {
            int[] domain = aInstance.getDomains().get(station).stream().mapToInt(Integer::intValue).toArray();
            final IntVar intVar = VariableFactory.enumerated(Integer.toString(station.getID()), domain, solver);
            stationToVar.put(station, intVar);
        });
        Map<Pair<Station, Station>, Tuples> pairToTuples = new HashMap<>();
        aInstance.getStations().forEach(station -> {
            aInstance.getDomains().get(station).forEach(channel -> {
                // Find all co constraints
                constraintManager.getCOInterferingStations(station, channel).stream().filter(interferingStation -> aInstance.getDomains().containsKey(interferingStation)).forEach(interferingStation -> {
                    Pair<Station, Station> pair = Pair.of(station, interferingStation);
                    pairToTuples.putIfAbsent(pair, new Tuples(false));
                    pairToTuples.get(pair).add(channel, channel);
                });
                // Do the ADJ constriants
                constraintManager.getADJplusInterferingStations(station, channel).stream().filter(interferingStation -> aInstance.getDomains().containsKey(interferingStation)).forEach(interferingStation -> {
                    Pair<Station, Station> pair = Pair.of(station, interferingStation);
                    pairToTuples.putIfAbsent(pair, new Tuples(false));
                    pairToTuples.get(pair).add(channel, channel + 1);
                });
            });
        });
        pairToTuples.entrySet().forEach(entry -> {
            IntVar v1 = stationToVar.get(entry.getKey().getLeft());
            IntVar v2 = stationToVar.get(entry.getKey().getRight());
            Preconditions.checkNotNull(v1);
            Preconditions.checkNotNull(v2);
            solver.post(ICF.table(v1, v2, entry.getValue(), "AC3bit+rm"));
        });
        SearchMonitorFactory.limitTime(solver, (long) aTerminationCriterion.getRemainingTime() * 1000);
        SearchMonitorFactory.limitSolution(solver, 1);
        // TODO: you'd probably have to PBO the restarts and search strategy before you got any performance out of this...
        SearchMonitorFactory.luby(solver, 5000, 200, new BacktrackCounter(5000), 1000);
        solver.set(ISF.activity(stationToVar.values().toArray(new IntVar[stationToVar.values().size()]), aSeed));
        log.info("Starting to solve problem");
        if (solver.findSolution()) {
            final Solution lastSolution = solver.getSolutionRecorder().getLastSolution();
            final Map<Integer, Set<Station>> assignment = new HashMap<>();
            stationToVar.entrySet().forEach(entry -> {
                Station station = entry.getKey();
                IntVar var = entry.getValue();
                int assignedChannel = lastSolution.getIntVal(var);
                assignment.putIfAbsent(assignedChannel, new HashSet<>());
                assignment.get(assignedChannel).add(station);
            });
            return new SolverResult(SATResult.SAT, watch.getElapsedTime(), assignment);
        } else {
            return super.solve(aInstance, aTerminationCriterion, aSeed);
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
    }
}
