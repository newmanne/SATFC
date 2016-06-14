package ca.ubc.cs.beta.fcc.simulator.scoring;

import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;

/**
 * Created by newmanne on 2016-06-14.
 */
public interface IScoringRule {

    double score(StationInfo s);

}
