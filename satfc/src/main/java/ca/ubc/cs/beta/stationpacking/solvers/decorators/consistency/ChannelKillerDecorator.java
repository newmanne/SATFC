package ca.ubc.cs.beta.stationpacking.solvers.decorators.consistency;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 10/08/15.
 */
@Slf4j
public class ChannelKillerDecorator extends ASolverDecorator {

    private final ISolver SATSolver;
    private final IConstraintManager constraintManager;

    public ChannelKillerDecorator(ISolver aSolver, ISolver SATSolver, IConstraintManager constraintManager) {
        super(aSolver);
        this.SATSolver = SATSolver;
        this.constraintManager = constraintManager;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        // Deep copy map
        final Map<Station, Set<Integer>> domainsCopy = aInstance.getDomains().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> new HashSet<>(entry.getValue())));
        final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domainsCopy, constraintManager);
        final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(constraintGraph);
        for (Map.Entry<Station, Set<Integer>> entry : domainsCopy.entrySet()) {
            final Station station = entry.getKey();
            final Set<Integer> domain = entry.getValue();
            final Set<Station> neighbours = neighborIndex.neighborsOf(station);
            final Map<Station, Set<Integer>> neighbourDomains = neighbours.stream().collect(Collectors.toMap(Function.identity(), domainsCopy::get));
            final Iterator<Integer> iterator = domain.iterator();
            while (iterator.hasNext()) {
                final int channel = iterator.next();
                neighbourDomains.put(station, ImmutableSet.of(channel));
                final StationPackingInstance reducedInstance = new StationPackingInstance(neighbourDomains);
                final SolverResult subResult = SATSolver.solve(reducedInstance, aTerminationCriterion, aSeed);
                if (subResult.getResult().equals(SATResult.UNSAT)) {
                    log.debug("Station {} on channel {} is UNSAT with its neigbhours, removing channel!", station, channel);
                    iterator.remove();
                }
                neighbourDomains.remove(station);
            }
        }
        // Remove any previous assignments that are no longer on their domains!
        final ImmutableMap<Station, Integer> reducedAssignment = aInstance.getPreviousAssignment().entrySet().stream().filter(entry -> domainsCopy.get(entry.getKey()).contains(entry.getValue())).collect(GuavaCollectors.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
        final StationPackingInstance reducedInstance = new StationPackingInstance(domainsCopy, reducedAssignment, aInstance.getMetadata());
        return fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
    }

    @Override
    public void notifyShutdown() {
        super.notifyShutdown();
        SATSolver.notifyShutdown();
    }

    @Override
    public void interrupt() {
        super.interrupt();
        SATSolver.interrupt();
    }
}
