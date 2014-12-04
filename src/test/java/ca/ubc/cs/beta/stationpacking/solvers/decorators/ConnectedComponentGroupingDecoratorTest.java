package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.IComponentGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Set;

import static org.mockito.Mockito.*;

public class ConnectedComponentGroupingDecoratorTest {

    @Test
    public void testSplitIntoComponents() {
        // should test that solve is called # components times on the decorator for a SAT problem
        final long seed = 0;
        final ISolver solver = mock(ISolver.class);
        final IComponentGrouper grouper = mock(IComponentGrouper.class);
        final IConstraintManager constraintManager = mock(IConstraintManager.class);
        final ITerminationCriterion terminationCriterion = mock(ITerminationCriterion.class);
        final ConnectedComponentGroupingDecorator connectedComponentGroupingDecorator = new ConnectedComponentGroupingDecorator(solver, grouper, constraintManager, 1);

        final StationPackingInstance instance = new StationPackingInstance(Maps.newHashMap());
        final Set<Station> componentA = mock(Set.class);
        final Set<Station> componentB = mock(Set.class);
        final Set<Station> componentC = mock(Set.class);
        final Set<Set<Station>> components = Sets.newHashSet(componentA, componentB, componentC);

        when(grouper.group(instance, constraintManager)).thenReturn(components);
        when(solver.solve(any(StationPackingInstance.class), eq(terminationCriterion), eq(seed))).thenReturn(new SolverResult(SATResult.SAT, 0, Maps.newHashMap()));
        connectedComponentGroupingDecorator.solve(instance, terminationCriterion, seed);

        verify(solver, times(components.size())).solve(any(StationPackingInstance.class), eq(terminationCriterion), eq(seed));
    }

    @Test
    public void testEarlyStopping() {
        // should test that if a problem has 3 UNSAT components and 2 threads, then the code examines at most 2 components
        final long seed = 0;
        final ISolver solver = mock(ISolver.class);
        final IComponentGrouper grouper = mock(IComponentGrouper.class);
        final IConstraintManager constraintManager = mock(IConstraintManager.class);
        final ITerminationCriterion terminationCriterion = mock(ITerminationCriterion.class);
        final ConnectedComponentGroupingDecorator connectedComponentGroupingDecorator = new ConnectedComponentGroupingDecorator(solver, grouper, constraintManager, 2);

        final StationPackingInstance instance = new StationPackingInstance(Maps.newHashMap());
        final Set<Station> componentA = mock(Set.class);
        final Set<Station> componentB = mock(Set.class);
        final Set<Station> componentC = mock(Set.class);
        final Set<Set<Station>> components = Sets.newHashSet(componentA, componentB, componentC);

        when(grouper.group(instance, constraintManager)).thenReturn(components);
        final SolverResult unsat = new SolverResult(SATResult.UNSAT, 0, Maps.newHashMap());
        when(solver.solve(any(StationPackingInstance.class), eq(terminationCriterion), eq(seed))).thenReturn(unsat);
        connectedComponentGroupingDecorator.solve(instance, terminationCriterion, seed);

        verify(solver, atMost(2)).solve(any(StationPackingInstance.class), eq(terminationCriterion), eq(seed));
    }


}
