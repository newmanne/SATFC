package ca.ubc.cs.beta.fcc.simulator.station.decorators;

import ca.ubc.cs.beta.fcc.simulator.station.AStationInfoDecorator;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;

/**
 * Created by newmanne on 2016-08-15.
 */
public class UnitValueDecorator extends AStationInfoDecorator {

    public UnitValueDecorator(IStationInfo decorated) {
        super(decorated);
    }

    @Override
    public Double getValue() {
        return 1.0;
    }

}
