package ca.ubc.cs.beta.fcc.simulator.state;

import lombok.Getter;

/**
 * Created by newmanne on 2016-11-02.
 */
public class RoundTracker {

    @Getter
    private int round;

    public RoundTracker() {
        round = 0;
    }

    public void incrementRound() {
        round++;
    }

}
