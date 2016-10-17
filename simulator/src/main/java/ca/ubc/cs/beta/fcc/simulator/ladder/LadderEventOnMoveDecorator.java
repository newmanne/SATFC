package ca.ubc.cs.beta.fcc.simulator.ladder;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by newmanne on 2016-10-11.
 */
public class LadderEventOnMoveDecorator extends ALadderDecorator {

    Collection<ILadderEventOnMoveListener> listeners;

    public LadderEventOnMoveDecorator(IModifiableLadder aLadder) {
        super(aLadder);
        listeners = new ArrayList<>();
    }

    public void addListener(ILadderEventOnMoveListener aLadderEventListener) {
        listeners.add(aLadderEventListener);
    }

    public void removeListener(ILadderEventOnMoveListener aLadderEventListener) {
        listeners.remove(aLadderEventListener);
    }

    @Override
    public void moveStation(IStationInfo station, Band band) {
        final Band prevBand = decorated.getStationBand(station);
        super.moveStation(station, band);
        for (ILadderEventOnMoveListener listener : listeners) {
            listener.onMove(station, prevBand, band, this);
        }

    }
}
