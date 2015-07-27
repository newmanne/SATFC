package ca.ubc.cs.beta.stationpacking.solvers.certifiers.cgneighborhood.strategies;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import com.google.common.collect.AbstractIterator;
import lombok.RequiredArgsConstructor;

import java.util.Iterator;
import java.util.Set;

/**
* Created by newmanne on 27/07/15.
*/
@RequiredArgsConstructor
public class TimeOutOnEachLayerOnceConfigurationStrategy implements IStationPackingConfigurationStrategy {

    private final IStationAddingStrategy stationAddingStrategy;

    @Override
    public Iterable<StationPackingConfiguration> getConfigurations(ITerminationCriterion terminationCriterion, StationPackingInstance stationPackingInstance, Set<Station> missingStations) {
        final Iterator<Set<Station>> stationsToPackIterator = stationAddingStrategy.getStationsToPack(stationPackingInstance, missingStations).iterator();
        return () -> new AbstractIterator<StationPackingConfiguration>() {

            @Override
            protected StationPackingConfiguration computeNext() {
                if (stationsToPackIterator.hasNext()) {
                    return new StationPackingConfiguration(terminationCriterion.getRemainingTime(), stationsToPackIterator.next());
                }
                return endOfData();
            }
        };
    }
}
