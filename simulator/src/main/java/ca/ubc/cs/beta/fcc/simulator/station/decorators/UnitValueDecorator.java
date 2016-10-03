package ca.ubc.cs.beta.fcc.simulator.station.decorators;

import ca.ubc.cs.beta.fcc.simulator.station.AStationInfoDecorator;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Created by newmanne on 2016-08-15.
 */
public class UnitValueDecorator extends AStationInfoDecorator {

    public UnitValueDecorator(IStationInfo decorated) {
        super(decorated);
    }

    @Override
    public Map<Band, Double> getValues() {
        return ImmutableMap.of(Band.LVHF, 1., Band.HVHF, 1., Band.UHF, 1.);
    }

}
