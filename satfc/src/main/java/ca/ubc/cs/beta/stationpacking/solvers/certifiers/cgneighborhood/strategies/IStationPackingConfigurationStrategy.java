package ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

import java.util.Set;

/**
* Created by newmanne on 27/07/15.
*/
public interface IStationPackingConfigurationStrategy {

    Iterable<StationPackingConfiguration> getConfigurations(ITerminationCriterion terminationCriterion, StationPackingInstance stationPackingInstance, Set<Station> missingStations);

}
