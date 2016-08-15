package ca.ubc.cs.beta.fcc.simulator.station.decorators;

import ca.ubc.cs.beta.fcc.simulator.station.AStationInfoDecorator;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;

/**
 * Created by newmanne on 2016-06-20.
 */
public class UnitVolumeDecorator extends AStationInfoDecorator {

    public UnitVolumeDecorator(IStationInfo decorated) {
        super(decorated);
    }

    @Override
    public Double getVolume() {
        return 1.;
    }

}
