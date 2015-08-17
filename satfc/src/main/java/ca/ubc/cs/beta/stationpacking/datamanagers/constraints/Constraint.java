package ca.ubc.cs.beta.stationpacking.datamanagers.constraints;

import ca.ubc.cs.beta.stationpacking.base.Station;
import lombok.Value;

/**
 * Representation of a binary constraint
 */
@Value
public class Constraint {
    private final Station source;
    private final Station target;
    private final int sourceChannel;
    private final int targetChannel;
}