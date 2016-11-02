package ca.ubc.cs.beta.fcc.simulator.ladder;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.eventbus.EventBus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by newmanne on 2016-10-11.
 */
public class LadderEventOnMoveDecorator extends ALadderDecorator {

    private final EventBus eventBus;

    public LadderEventOnMoveDecorator(IModifiableLadder aLadder, EventBus eventBus) {
        super(aLadder);
        this.eventBus = eventBus;
    }

    @Override
    public void moveStation(IStationInfo station, Band band) {
        final Band prevBand = decorated.getStationBand(station);
        super.moveStation(station, band);
        eventBus.post(new LadderMoveEvent(station, prevBand, band, this));
    }

    @Value
    public static class LadderMoveEvent {
        private IStationInfo station;
        private Band prevBand;
        private Band newBand;
        private ILadder ladder;
    }

}
