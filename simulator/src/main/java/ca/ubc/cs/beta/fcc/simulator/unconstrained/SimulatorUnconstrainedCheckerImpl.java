package ca.ubc.cs.beta.fcc.simulator.unconstrained;

import ca.ubc.cs.beta.fcc.simulator.DomainChangeEvent;
import ca.ubc.cs.beta.fcc.simulator.ladder.ILadder;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableTable;
import com.google.common.eventbus.Subscribe;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by newmanne on 2016-09-27.
 * I think this is a bit stronger than what was used in appendix D, which never recalculates A^{c}_{su}
 */
@Slf4j
public class SimulatorUnconstrainedCheckerImpl implements ISimulatorUnconstrainedChecker {

    private final IConstraintManager constraintManager;
    private final ParticipationRecord participationRecord;
    private ImmutableTable<IStationInfo, Band, Set<IStationInfo>> bandNeighborIndexMap;

    // Index 1: s, Index 2: u, Index 3: Maximum number of channels that s can block on u's home band
    private ImmutableTable<IStationInfo, IStationInfo, Integer> blockingTable;

    private boolean dirtyBit;

    public SimulatorUnconstrainedCheckerImpl(@NonNull IConstraintManager constraintManager, @NonNull ParticipationRecord participationRecord, @NonNull ILadder ladder) {
        this.constraintManager = constraintManager;
        this.participationRecord = participationRecord;
        init(constraintManager, ladder);
    }

    private void init(@NonNull IConstraintManager constraintManager, @NonNull ILadder ladder) {
        bandNeighborIndexMap = SimulatorUtils.getBandNeighborIndexMap(ladder, constraintManager);
        final ImmutableTable.Builder<IStationInfo, IStationInfo, Integer> builder = ImmutableTable.builder();

        log.info("Filling in unconstrained table for {} stations", participationRecord.getActiveStations().size());
        // We might need to check if any participating station is unconstrained
        for (IStationInfo u : participationRecord.getActiveStations()) {
            // Who are its neighbours in the interference graph of its home band?
            final Band b = u.getHomeBand();
            for (IStationInfo s : bandNeighborIndexMap.get(u, b)) {
                int overallMax = 0;
                // For each channel that neighbour could go on, how many channels on u's domain could it block?
                for (int c : s.getDomain(b)) {
                    // the number of channels s blocks for u when s is on c
                    int Acsu = u.getDomain(b).stream()
                            .filter(uChan -> Math.abs(c - uChan) <= 2)
                            .mapToInt(uChan -> BooleanUtils.toInteger(constraintManager.isSatisfyingAssignment(s.toSATFCStation(), c, u.toSATFCStation(), uChan)))
                            .sum();
                    log.trace("Station {} on {} blocks {} channels of {}", s, c, Acsu, u);
                    Preconditions.checkState(Acsu >= 0 && Acsu <= 5, "Unconstrained weird value of %s", Acsu);
                    overallMax = Math.max(overallMax, Acsu);
                }
                builder.put(s, u, overallMax);
            }
        }
        blockingTable = builder.build();
        dirtyBit = false;
    }

    @Override
    public boolean isUnconstrained(@NonNull IStationInfo stationInfo, @NonNull ILadder ladder) {
        if (dirtyBit) {
            init(constraintManager, ladder);
        }

        // 1) Get the set of stations that could ever wind up in the station's home band
        final Band homeBand = stationInfo.getHomeBand();
        final Set<IStationInfo> possibleStations = new HashSet<>();
        for (IStationInfo station : ladder.getStations()) {
            final Band stationBand = ladder.getStationBand(station);
            if (stationBand.equals(homeBand) || // they are already there
                    (stationBand.isBelow(homeBand) // OR: below home band, not a provisional winner, and home band is permissible for them
                            && !participationRecord.getParticipation(station).equals(Participation.FROZEN_PROVISIONALLY_WINNING)
                            && ladder.getPermissibleOptions(station).contains(homeBand))) {
                // Only worth adding if they are actual neighbours, otherwise they block 0 channels
                if (bandNeighborIndexMap.get(stationInfo, homeBand).contains(station)) {
                    possibleStations.add(station);
                }
            }
        }

        // 2) For every station that might block a channel, how many channels could they block?
        final int blocked = possibleStations.stream()
                .mapToInt(s -> blockingTable.get(s, stationInfo))
                .sum();

        // 3) If this is less than the number of channels the station has in its home band, the station is unconstrained
        return blocked < stationInfo.getDomain(homeBand).size();
    }

    @Subscribe
    public void onDomainChanged(DomainChangeEvent event) {
        dirtyBit = true;
        // Don't just call init here, because the ParticipationRecord is fragile when stations unimpair
    }


}
