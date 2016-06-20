package ca.ubc.cs.beta.fcc.simulator.station;

/**
 * Created by newmanne on 2016-06-20.
 */
public class UnitVolumeDecorator extends AStationInfoDecorator {

    public UnitVolumeDecorator(IStationInfo decorated) {
        super(decorated);
    }

    @Override
    public Integer getVolume() {
        return 1;
    }

}
