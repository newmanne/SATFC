package ca.ubc.cs.beta.fcc.simulator.parameters;

import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import lombok.Data;
import lombok.experimental.Builder;

import java.util.Map;

/**
 * Created by newmanne on 2016-10-02.
 */
@Data
@Builder
public class LadderAuctionParameters {

    private final double VAC_FLOOR;
    private final double r1;
    private final double r2;
    private final Map<Band, Double> openingBenchmarkPrices;

}
