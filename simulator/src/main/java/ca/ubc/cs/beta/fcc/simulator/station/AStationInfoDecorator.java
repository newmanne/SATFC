package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.ImmutableSet;

import java.util.Map;

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
    public Map<Band, Double> getValues() {
        return decorated.getValues();
    }

    @Override
    public Nationality getNationality() {
        return decorated.getNationality();
    }

    @Override
    public ImmutableSet<Integer> getDomain() {
        return decorated.getDomain();
    }

    @Override
    public Bid queryPreferredBand(Map<Band, Double> offers, Band currentBand) {
        return decorated.queryPreferredBand(offers, currentBand);
    }

    @Override
    public Band getHomeBand() {
        return decorated.getHomeBand();
    }

    @Override
    public String getCity() {
        return decorated.getCity();
    }

    @Override
    public String getCall() {
        return decorated.getCall();
    }
}
