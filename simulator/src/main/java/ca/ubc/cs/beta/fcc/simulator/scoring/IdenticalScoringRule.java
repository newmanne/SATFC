package ca.ubc.cs.beta.fcc.simulator.scoring;

import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import lombok.RequiredArgsConstructor;

/**
 * Scores each station exactly the same
 */
@RequiredArgsConstructor
public class IdenticalScoringRule implements IScoringRule {

    private final double startingPrice;

    @Override
    public double score(StationInfo s) {
        return startingPrice;
    }
}
