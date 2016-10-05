package ca.ubc.cs.beta.fcc.simulator.unconstrained;

import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.BandHelper;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-09-27.
 */
@Slf4j
public class SimulatorUnconstrainedCheckerImpl implements ISimulatorUnconstrainedChecker {

    private final ParticipationRecord participationRecord;
    private final ImmutableMap<UnconstrainedKey, Integer> icMap;

    public SimulatorUnconstrainedCheckerImpl(@NonNull IConstraintManager constraintManager, @NonNull ParticipationRecord participationRecord) {
        this.participationRecord = participationRecord;
        final ImmutableMap.Builder<UnconstrainedKey, Integer> builder = ImmutableMap.builder();
        final ImmutableSet<IStationInfo> stations = participationRecord.getStations();
        final Map<Station, Set<Integer>> domains = stations.stream().collect(Collectors.toMap(IStationInfo::toSATFCStation, IStationInfo::getDomain));
        final SimpleGraph<Station, DefaultEdge> constraintGraph = ConstraintGrouper.getConstraintGraph(domains, constraintManager);
        final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(constraintGraph);
        log.info("Filling in unconstrained table for {} stations", stations.size());
        for (Station s : domains.keySet()) {
            for (int c : domains.get(s)) {
                // s is on c
                final Set<Station> neighbours = neighborIndex.neighborsOf(s);
                for (Station u : domains.keySet()) {
                    int Acsu = 0;
                    if (neighbours.contains(u)) {
                        Acsu = domains.get(u).stream()
                                .filter(uChan -> Math.abs(c - uChan) <= 2)
                                .mapToInt(uChan -> {
                                    final Map<Station, Set<Integer>> d2 = ImmutableMap.of(s, ImmutableSet.of(c), u, ImmutableSet.of(uChan));
                                    return BooleanUtils.toInteger(Iterables.isEmpty(constraintManager.getAllRelevantConstraints(d2)));
                                })
                                .sum();
                        log.trace("Station {} on {} blocks {} channels of {}", s, c, Acsu, u);
                    }
                    Preconditions.checkState(Acsu >= 0 && Acsu <= 5, "Unconstrained weird value of %s", Acsu);
                    // number of applicable constraints limiting assignment of u to {c-2...c+2} when s is on c
                    builder.put(new UnconstrainedKey(s.getID(), u.getID(), c), Acsu);
                }
            }
        }
        icMap = builder.build();
    }

    /**
     * @return The number of channels that are blocked for u in {c-2 ... c+2} if s is placed on c
     */
    private int getBlocked(IStationInfo s, IStationInfo u, int c) {
        final UnconstrainedKey key = new UnconstrainedKey(s.getId(), u.getId(), c);
        Integer count = icMap.get(key);
        Preconditions.checkState(count != null, "No key was precomputed for %s on %s and %s", s, u, c);
        return count;
    }

    @Override
    public boolean isUnconstrained(@NonNull IStationInfo stationInfo, @NonNull ILadder ladder) {
        final Band homeBand = stationInfo.getHomeBand();
        final Set<IStationInfo> possibleStations = new HashSet<>();
        for (IStationInfo station : ladder.getStations()) {
            final Band stationBand = ladder.getStationBand(station);
            if (stationBand.equals(homeBand) || // currently held option is the same
                    (stationBand.isBelow(homeBand) // OR: below option, not a provisional winner, and its permissible
                            && !participationRecord.getParticipation(station).equals(Participation.FROZEN_PROVISIONALLY_WINNING)
                            && ladder.getPossibleMoves(station).contains(homeBand))) {
                possibleStations.add(station);
            }
        }
        final int blocked = possibleStations.stream()
                .mapToInt(competingStation -> competingStation.getDomain(homeBand).stream()
                        .mapToInt(channel -> getBlocked(competingStation, stationInfo, channel))
                        .max()
                        .getAsInt()
                )
                .sum();
        return blocked < stationInfo.getDomain(homeBand).size();
    }

    @Value
    private static class UnconstrainedKey {
        private final int s;
        private final int u;
        private final int c;
    }

}
