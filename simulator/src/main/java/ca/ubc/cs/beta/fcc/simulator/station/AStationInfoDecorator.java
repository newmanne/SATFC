package ca.ubc.cs.beta.fcc.simulator.station;

/**
 * Created by newmanne on 2016-06-20.
 */
public abstract class AStationInfoDecorator implements IStationInfo {

    private final IStationInfo decorated;

    public AStationInfoDecorator(IStationInfo decorated) {
        this.decorated = decorated;
    }

    @Override
    public int getId() {
        return decorated.getId();
    }

    @Override
    public Double getVolume() {
        return decorated.getVolume();
    }

    @Override
    public Double getValue() {
        return decorated.getValue();
    }

    @Override
    public Nationality getNationality() {
        return decorated.getNationality();
    }

}
