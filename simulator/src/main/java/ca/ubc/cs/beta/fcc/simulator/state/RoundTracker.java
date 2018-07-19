package ca.ubc.cs.beta.fcc.simulator.state;

import lombok.Getter;

/**
 * Created by newmanne on 2016-11-02.
 */
public class RoundTracker {

    @Getter
    private int round;

    @Getter
    private int stage;

    public RoundTracker() {
        round = 0;
        stage = 1;
    }

    public void incrementRound() {
        round++;
    }

    public void incrementStage() {
        stage++;
        round = 0;
    }

}
