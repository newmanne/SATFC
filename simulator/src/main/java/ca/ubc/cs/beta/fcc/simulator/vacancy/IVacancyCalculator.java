package ca.ubc.cs.beta.fcc.simulator.vacancy;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;

import java.util.Collection;
import java.util.Map;

/**
 * Created by newmanne on 2016-07-26.
 */

public interface IVacancyCalculator {

    ImmutableTable<IStationInfo, Band, Double> computeVacancies(Collection<IStationInfo> stations, ILadder aLadder, Map<Integer, Integer> aCurrentChannelAssignment, IPrices previousBenchmarkPrices);

}
