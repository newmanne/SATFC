package ca.ubc.cs.beta.stationpacking.consistency;

import ca.ubc.cs.beta.stationpacking.base.Station;
import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
* Created by newmanne on 10/06/15.
*/
@Data
public class AC3Output {
    private boolean noSolution = false;
    private final Map<Station, Set<Integer>> reducedDomains;
    private int numReducedChannels = 0;
}
