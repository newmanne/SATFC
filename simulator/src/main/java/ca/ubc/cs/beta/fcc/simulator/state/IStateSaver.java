package ca.ubc.cs.beta.fcc.simulator.state;

import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.Prices;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.time.TimeTracker;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Builder;

import java.util.Map;

/**
 * Created by newmanne on 2016-05-25.
 */
public interface IStateSaver {

    void saveState(StationDB stationDB, Prices prices, ParticipationRecord participation, Map<Integer, Integer> assignment, int round, Map<SATResult, Integer> feasibilityResultDistribution, TimeTracker timeTracker);

    AuctionState restoreState(StationDB stationDB);

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AuctionState {

        private Prices prices;
        private ParticipationRecord participation;
        private int round;
        private Map<Integer, Integer> assignment;
    }

}
