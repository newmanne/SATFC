package ca.ubc.cs.beta.fcc.simulator.state;

import ca.ubc.cs.beta.fcc.simulator.ladder.IModifiableLadder;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.ImmutableTable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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

    private IPrices offers;
    private ImmutableTable<IStationInfo, Band, Double> vacancies;
    private ImmutableTable<IStationInfo, Band, Double> reductionCoefficients;
    private List<IStationInfo> bidProcessingOrder;

    // The current compensation of every station
    private Map<IStationInfo, Double> prices;
    // UHF to off benchmark
    private double baseClockPrice;


}
