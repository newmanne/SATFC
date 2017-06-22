package ca.ubc.cs.beta.fcc.simulator.participation;

import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.RandomUtils;

/**
 * Created by newmanne on 2016-10-26.
 */
// Extend OpeningPrice because it still wouldn't make sense to participate if prices are lower than your value?
public class UniformParticipationDecider extends OpeningOffPriceHigherThanPrivateValue {

    private final double uniformProbability;

    public UniformParticipationDecider(double uniformProbability, IPrices prices) {
        super(prices);
        this.uniformProbability = uniformProbability;
    }

    @Override
    public boolean isParticipating(IStationInfo s) {
        boolean priceCompatible = super.isParticipating(s);
        return priceCompatible && uniformProbability >= RandomUtils.getRandom().nextDouble();
    }
}
