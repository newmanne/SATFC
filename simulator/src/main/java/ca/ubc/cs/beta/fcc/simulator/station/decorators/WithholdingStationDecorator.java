package ca.ubc.cs.beta.fcc.simulator.station.decorators;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.station.AStationInfoDecorator;
import ca.ubc.cs.beta.fcc.simulator.station.IModifiableStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class WithholdingStationDecorator extends AStationInfoDecorator {

    public WithholdingStationDecorator(IModifiableStationInfo decorated) {
        super(decorated);
    }

    @Override
    public Bid queryPreferredBand(Map<Band, Long> offers, Band currentBand) {
        log.info("{} withholding station and dropping out", getId());
        return new Bid(getHomeBand(), null);
    }

}
