package ca.ubc.cs.beta.fcc.simulator.station;

import ca.ubc.cs.beta.fcc.simulator.utils.Band;

import java.util.Map;

public interface IModifiableStationInfo extends IStationInfo {

    void setMaxChannel(int c);
    void setMinChannel(int c);
    void setValues(Map<Band, Long> values);
    void setCommercial(Boolean commercial);
    void setVolume(Integer volume);

}
