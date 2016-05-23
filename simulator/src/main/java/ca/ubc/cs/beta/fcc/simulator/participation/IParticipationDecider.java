package ca.ubc.cs.beta.fcc.simulator.participation;

import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;

/**
 * Created by newmanne on 2016-05-20.
 */
public interface IParticipationDecider {

    boolean isParticipating(StationInfo s);

}
