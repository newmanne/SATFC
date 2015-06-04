package ca.ubc.cs.beta.stationpacking;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;

import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.Converter;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Created by newmanne on 21/04/15.
 */
public class StationPackingTestUtils {

    public static StationPackingInstance getSimpleInstance() {
        return new StationPackingInstance(ImmutableMap.of(new Station(1), ImmutableSet.of(1)));
    }

    public static Map<Integer, Set<Station>> getSimpleInstanceAnswer() {
        return ImmutableMap.of(1, ImmutableSet.of(new Station(1)));
    }

    public static StationPackingInstance instanceFromSpecs(Converter.StationPackingProblemSpecs specs, IStationManager stationManager) {
        final Map<Station, Set<Integer>> domains = new HashMap<>();
        for (Integer stationID : specs.getDomains().keySet()) {
            Station station = stationManager.getStationfromID(stationID);

            Set<Integer> domain = specs.getDomains().get(stationID);
            Set<Integer> stationDomain = stationManager.getDomain(station);

            Set<Integer> truedomain = Sets.intersection(domain, stationDomain);

            domains.put(station, truedomain);
        }

        final Map<Station, Integer> previousAssignment = new HashMap<>();
        for (Station station : domains.keySet()) {
            Integer previousChannel = specs.getPreviousAssignment().get(station.getID());
            if (previousChannel != null && previousChannel > 0) {
                previousAssignment.put(station, previousChannel);
            }
        }

        return new StationPackingInstance(domains, previousAssignment);
    }

}
