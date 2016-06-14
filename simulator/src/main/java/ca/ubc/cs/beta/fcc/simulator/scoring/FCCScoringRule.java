package ca.ubc.cs.beta.fcc.simulator.scoring;

import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import lombok.RequiredArgsConstructor;

/**
 * Created by newmanne on 2016-06-14.
 */
@RequiredArgsConstructor
public class FCCScoringRule implements IScoringRule {

    private final double baseClock;

    @Override
    public double score(StationInfo s) {
        return baseClock * s.getVolume();
    }

}
