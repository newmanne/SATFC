package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-10-04.
 */
public class UnitVolumeCalculator implements SimulatorParameters.IVolumeCalculator {

    @Override
    public Map<Integer, Integer> getVolumes(Set<IStationInfo> stations) {
        return stations.stream().collect(Collectors.toMap(IStationInfo::getId, s -> 1));
    }

}
