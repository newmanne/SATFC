package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.ImmutableSet;

import java.util.Map;

/**
 * Created by newmanne on 2016-06-20.
 */
public abstract class AStationInfoDecorator implements IModifiableStationInfo {

    private final IModifiableStationInfo decorated;

    public AStationInfoDecorator(IModifiableStationInfo decorated) {
        this.decorated = decorated;
    }

    @Override
    public int getId() {
        return decorated.getId();
    }

    @Override
    public Integer getVolume() {
        return decorated.getVolume();
    }

    @Override
    public Map<Band, Long> getValues() {
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
    public Bid queryPreferredBand(Map<Band, Long> offers, Band currentBand) {
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

    @Override
    public Boolean isCommercial() {
        return decorated.isCommercial();
    }

    @Override
    public void impair() {
        decorated.impair();
    }

    @Override
    public void unimpair() {
        decorated.unimpair();
    }

    @Override
    public boolean isImpaired() {
        return decorated.isImpaired();
    }

    @Override
    public ImmutableSet<Integer> getFullDomain() {
        return decorated.getFullDomain();
    }

    @Override
    public int getPopulation() {
        return decorated.getPopulation();
    }

    @Override
    public boolean isEligible() {
        return decorated.isEligible();
    }

    @Override
    public String getDMA() {
        return decorated.getDMA();
    }

    @Override
    public void setValues(Map<Band, Long> values) {
        decorated.setValues(values);
    }

    @Override
    public void setMaxChannel(int c) {
        decorated.setMaxChannel(c);
    }

    @Override
    public void setMinChannel(int c) {
        decorated.setMinChannel(c);
    }

    @Override
    public void setCommercial(Boolean commercial) {
        decorated.setCommercial(commercial);
    }

    @Override
    public void setVolume(Integer volume) {
        decorated.setVolume(volume);
    }
}
