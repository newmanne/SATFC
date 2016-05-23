package ca.ubc.cs.beta.fcc.simulator.participation;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;

/**
 * Created by newmanne on 2016-05-20.
 */
public class OpeningPriceHigherThanPrivateValue implements IParticipationDecider {

    private final Simulator.Prices prices;

    public OpeningPriceHigherThanPrivateValue(Simulator.Prices prices) {
        this.prices = prices;
    }

    @Override
    public boolean isParticipating(StationInfo s) {
        return prices.getPrice(s) >= s.getValue();
    }

}
