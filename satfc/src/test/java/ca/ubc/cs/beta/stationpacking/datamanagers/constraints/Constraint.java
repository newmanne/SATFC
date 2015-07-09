package ca.ubc.cs.beta.stationpacking.datamanagers.constraints;

import ca.ubc.cs.beta.stationpacking.base.Station;
import lombok.Data;

import java.util.Set;

/**
* Created by newmanne on 08/07/15.
*/
@Data
public class Constraint {
    private final AConstraintManager.ConstraintKey key;
    private final int channel;
    private final Station reference;
    private final Set<Station> interfering;
}
