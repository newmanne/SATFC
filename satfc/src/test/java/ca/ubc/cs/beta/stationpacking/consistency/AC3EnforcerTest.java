package ca.ubc.cs.beta.stationpacking.consistency;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.TestConstraintManager;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.util.*;

import static ca.ubc.cs.beta.stationpacking.datamanagers.constraints.AConstraintManager.ConstraintKey.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class AC3EnforcerTest {

    final Station s1 = new Station(1);
    final Station s2 = new Station(2);
    final Station s3 = new Station(3);

    @Test
    public void testAC3() throws Exception {
        final StationPackingInstance instance = new StationPackingInstance(
                ImmutableMap.of(
                        s1, ImmutableSet.of(1),
                        s2, ImmutableSet.of(1, 2, 3),
                        s3, ImmutableSet.of(1, 2, 3)
                )
        );
        List<Constraint> constraints = new ArrayList<>();
        constraints.add(new Constraint(CO, 1, s1, ImmutableSet.of(s2, s3))); // CO,1,1,s1,s2,s3
        constraints.add(new Constraint(ADJp1, 1, s1, ImmutableSet.of(s2, s3))); // ADJ+1,1,2,s1,s2,s3
        IConstraintManager constraintManager = new TestConstraintManager(constraints);
        final AC3Enforcer ac3 = new AC3Enforcer(constraintManager);
        final AC3Output ac3Output = ac3.AC3(instance);
        assertFalse(ac3Output.isNoSolution());
        assertEquals(4, ac3Output.getNumReducedChannels());
        final Map<Station, Set<Integer>> reducedDomains = ac3Output.getReducedDomains();
        assertEquals(ImmutableSet.of(3), reducedDomains.get(s2));
        assertEquals(ImmutableSet.of(3), reducedDomains.get(s3));
    }

    @Test
    public void testNoSolution() throws Exception {
        final StationPackingInstance instance = new StationPackingInstance(
                ImmutableMap.of(
                        s1, ImmutableSet.of(1),
                        s2, ImmutableSet.of(1, 2)
                )
        );
        List<Constraint> constraints = new ArrayList<>();
        constraints.add(new Constraint(CO, 1, s1, ImmutableSet.of(s2)));
        constraints.add(new Constraint(ADJp1, 1, s1, ImmutableSet.of(s2)));
        IConstraintManager constraintManager = new TestConstraintManager(constraints);
        final AC3Enforcer ac3 = new AC3Enforcer(constraintManager);
        final AC3Output ac3Output = ac3.AC3(instance);
        assertTrue(ac3Output.isNoSolution());
    }

}