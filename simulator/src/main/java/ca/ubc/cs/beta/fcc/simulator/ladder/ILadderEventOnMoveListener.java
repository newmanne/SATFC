package ca.ubc.cs.beta.fcc.simulator.ladder;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;

/**
 * Created by newmanne on 2016-08-04.
 */
public interface ILadderEventOnMoveListener {

    void onMove(IStationInfo station, Band prevBand, Band newBand, ILadder ladder);

}
