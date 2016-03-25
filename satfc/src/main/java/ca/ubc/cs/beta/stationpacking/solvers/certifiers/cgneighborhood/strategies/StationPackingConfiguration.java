package ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies;

import java.util.Set;

import ca.ubc.cs.beta.stationpacking.base.Station;
import lombok.Value;

/**
* Created by newmanne on 27/07/15.
*/
@Value
public class StationPackingConfiguration {
    private final double cutoff;
    private final Set<Station> packingStations;
}
