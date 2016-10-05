package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;

import java.util.Set;

/**
 * Created by newmanne on 2016-10-04.
 */
public class UnitVolumeCalculator implements SimulatorParameters.IVolumeCalculator {
    @Override
    public void setVolumes(Set<StationInfo> stationInfo) {
        stationInfo.forEach(s -> s.setVolume(1.));
    }
}
