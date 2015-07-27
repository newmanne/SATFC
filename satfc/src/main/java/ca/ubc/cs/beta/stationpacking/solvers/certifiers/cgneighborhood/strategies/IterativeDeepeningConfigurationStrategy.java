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
* Iterate through an IStationAddingStrategy, starting with quick cutoffs and then looping back with larger ones
*/
@RequiredArgsConstructor
public class IterativeDeepeningConfigurationStrategy implements IStationPackingConfigurationStrategy {

    // Strategy for adding the next set of stations to pack
    private final IStationAddingStrategy stationAddingStrategy;
    // Whether or not to cycle over the IStationAddingStrategy (or stop once it ends)
    private final boolean loop;
    private final double baseCutoff;
    private final double scalingFactor;

    @Override
    public Iterable<StationPackingConfiguration> getConfigurations(ITerminationCriterion terminationCriterion, StationPackingInstance stationPackingInstance, Set<Station> missingStations) {
        final Iterable<Set<Station>> stationsToPackIterable = stationAddingStrategy.getStationsToPack(stationPackingInstance, missingStations);
        return () -> new AbstractIterator<StationPackingConfiguration>() {

            Iterator<Set<Station>> stationsToPackIterator = stationsToPackIterable.iterator();
            int iteration = 0;

            @Override
            protected StationPackingConfiguration computeNext() {
                if (!stationsToPackIterator.hasNext()) {
                    if (!loop) {
                        return endOfData();
                    } else {
                        stationsToPackIterator = stationsToPackIterable.iterator();
                        iteration++;
                        if (!stationsToPackIterator.hasNext()) { // 0 element iterator
                            return endOfData();
                        }
                    }
                }
                final Set<Station> toPack = stationsToPackIterator.next();
                final double cutoff = baseCutoff + iteration * scalingFactor;
                return new StationPackingConfiguration(cutoff, toPack);
            }
        };
    }
}
