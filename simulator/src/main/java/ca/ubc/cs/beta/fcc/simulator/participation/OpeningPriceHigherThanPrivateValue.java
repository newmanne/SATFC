package ca.ubc.cs.beta.fcc.simulator.participation;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by newmanne on 2016-05-20.
 */
@Slf4j
public class OpeningPriceHigherThanPrivateValue implements IParticipationDecider {

    private final IPrices prices;

    public OpeningPriceHigherThanPrivateValue(IPrices prices) {
        this.prices = prices;
    }

    @Override
    public boolean isParticipating(IStationInfo s) {
        final Bid bid = s.queryPreferredBand(prices.getPrices(s, s.getHomeBand().getBandsBelowInclusive()), Band.OFF);
        return bid.getPreferredOption().isBelow(s.getHomeBand());
    }

}
