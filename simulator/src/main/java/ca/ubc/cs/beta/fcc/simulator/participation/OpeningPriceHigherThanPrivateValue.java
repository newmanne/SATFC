package ca.ubc.cs.beta.fcc.simulator.participation;

import ca.ubc.cs.beta.fcc.simulator.prices.Prices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;

/**
 * Created by newmanne on 2016-05-20.
 */
public class OpeningPriceHigherThanPrivateValue implements IParticipationDecider {

    private final Prices prices;

    public OpeningPriceHigherThanPrivateValue(Prices prices) {
        this.prices = prices;
    }

    @Override
    public boolean isParticipating(IStationInfo s) {
        return prices.getPrice(s) >= s.getValue();
    }

}
