package ca.ubc.cs.beta.fcc.simulator.scoring;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import lombok.RequiredArgsConstructor;

/**
 * Created by newmanne on 2016-06-14.
 */
public class FCCScoringRule implements IScoringRule {

    @Override
    public double score(IStationInfo s, double base) {
        return base * s.getVolume();
    }

}
