package ca.ubc.cs.beta.fcc.simulator.catchup;

import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Created by newmanne on 2018-06-08.
 */
@Data
public class CatchupPoint {

    private final double catchUpPoint;
    private final Map<Band, Double> benchmarkPrices;

}
