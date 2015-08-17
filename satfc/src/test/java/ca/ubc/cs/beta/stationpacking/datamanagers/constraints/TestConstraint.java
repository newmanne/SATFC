package ca.ubc.cs.beta.stationpacking.datamanagers.constraints;

import java.util.Set;

import ca.ubc.cs.beta.stationpacking.base.Station;
import lombok.Data;

/**
* Created by newmanne on 08/07/15.
*/
@Data
public class TestConstraint {
    private final ConstraintKey key;
    private final int channel;
    private final Station reference;
    private final Set<Station> interfering;
}
