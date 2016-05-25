package ca.ubc.cs.beta.fcc.simulator.state;

import ca.ubc.cs.beta.fcc.simulator.Simulator;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Builder;

import java.util.Map;

/**
 * Created by newmanne on 2016-05-25.
 */
public interface IStateSaver {

    void saveState(StationDB stationDB, Simulator.Prices prices, ParticipationRecord participation, Map<Integer, Integer> assignment, int round);

    AuctionState restoreState(StationDB stationDB);

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AuctionState {

        private Simulator.Prices prices;
        private ParticipationRecord participation;
        private int round;
        private Map<Integer, Integer> assignment;
    }

}
