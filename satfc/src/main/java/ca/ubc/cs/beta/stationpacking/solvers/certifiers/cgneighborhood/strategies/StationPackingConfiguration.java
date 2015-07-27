package ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies;

import ca.ubc.cs.beta.stationpacking.base.Station;
import lombok.Value;

import java.util.Set;

/**
* Created by newmanne on 27/07/15.
*/
@Value
public class StationPackingConfiguration {
    private final double cutoff;
    private final Set<Station> packingStations;
}
