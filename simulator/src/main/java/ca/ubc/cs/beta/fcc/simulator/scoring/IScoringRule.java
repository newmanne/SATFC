package ca.ubc.cs.beta.fcc.simulator.scoring;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;

/**
 * Created by newmanne on 2016-06-14.
 */
public interface IScoringRule {

    double score(IStationInfo s, double base);

}
