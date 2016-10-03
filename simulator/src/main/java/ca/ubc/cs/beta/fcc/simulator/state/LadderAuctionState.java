package ca.ubc.cs.beta.fcc.simulator.state;

import ca.ubc.cs.beta.fcc.simulator.ladder.IModifiableLadder;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Builder;

import java.util.Map;

/**
 * Created by newmanne on 2016-09-27.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LadderAuctionState {

    private IPrices benchmarkPrices;
    private ParticipationRecord participation;
    private int round;
    private Map<Integer, Integer> assignment;
    private IModifiableLadder ladder;

    // The current compensation of every station
    private Map<IStationInfo, Double> prices;
    // UHF to off benchmark
    private double baseClockPrice;


}
