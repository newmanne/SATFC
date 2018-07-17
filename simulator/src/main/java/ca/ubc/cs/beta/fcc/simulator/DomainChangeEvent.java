package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Created by newmanne on 2018-06-11.
 */
@Data
public class DomainChangeEvent {

    private final ILadder ladder;
    private final IConstraintManager constraintManager;
}
