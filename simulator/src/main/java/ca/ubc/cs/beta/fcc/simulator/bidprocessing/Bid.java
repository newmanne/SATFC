package ca.ubc.cs.beta.fcc.simulator.bidprocessing;

import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import lombok.Value;

/**
 * Created by newmanne on 2016-10-02.
 */
@Value
public class Bid {
    private final Band preferredOption;
    private final Band fallbackOption;

    public static boolean isSafe(Band bidBand, Band current, Band home) {
        return bidBand.equals(home) || bidBand.equals(current);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(preferredOption);
        if (fallbackOption != null) {
            sb.append(", fallback to ").append(fallbackOption);
        }
        return sb.toString();
    }


}
