package ca.ubc.cs.beta.stationpacking.datamanagers.constraints;

import ca.ubc.cs.beta.stationpacking.base.Station;

import java.io.FileNotFoundException;
import java.util.*;

import static ca.ubc.cs.beta.stationpacking.datamanagers.constraints.AConstraintManager.ConstraintKey.CO;

/**
* Created by newmanne on 08/07/15.
*/
public class TestConstraintManager extends AConstraintManager {

    public TestConstraintManager(List<Constraint> constraints) throws FileNotFoundException {
        super();
        constraints.stream().forEach(c -> {
            Map<Station, Map<Integer, Set<Station>>> map = c.getKey().equals(CO) ? fCOConstraints : fADJp1Constraints;
            map.putIfAbsent(c.getReference(), new HashMap<>());
            map.get(c.getReference()).putIfAbsent(c.getChannel(), new HashSet<>());
            map.get(c.getReference()).get(c.getChannel()).addAll(c.getInterfering());
        });
    }

}
