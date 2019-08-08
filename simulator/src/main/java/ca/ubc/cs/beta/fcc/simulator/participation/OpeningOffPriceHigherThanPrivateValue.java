package ca.ubc.cs.beta.fcc.simulator.participation;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import humanize.Humanize;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by newmanne on 2016-05-20.
 * A station participates IFF its open price for going off air is higher than its private value
 */
@Slf4j
public class OpeningOffPriceHigherThanPrivateValue implements IParticipationDecider {

    private final IPrices<Long> prices;

    public OpeningOffPriceHigherThanPrivateValue(IPrices<Long> prices) {
        this.prices = prices;
    }

    @Override
    public boolean isParticipating(IStationInfo s) {
        final long value = s.getValue();
        final long offOffer = prices.getPrice(s, Band.OFF);
        final boolean participating = offOffer > value;
        if (!participating) {
            log.info("Station {} is not participating as its value {} is higher than it's opening price of {}", s, Humanize.spellBigNumber(s.getValue()), Humanize.spellBigNumber(prices.getPrice(s, Band.OFF)));
        }
        return participating;
    }

}
