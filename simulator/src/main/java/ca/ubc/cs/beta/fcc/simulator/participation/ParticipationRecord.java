package ca.ubc.cs.beta.fcc.simulator.participation;

import ca.ubc.cs.beta.fcc.simulator.station.IStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-05-20.
 */
public class ParticipationRecord {

    final Map<IStationInfo, Participation> participationMap;

    public ParticipationRecord() {
        participationMap = new ConcurrentHashMap<>();
    }

    public ParticipationRecord(IStationDB stationDB, IParticipationDecider participationDecider) {
        this();
        for (IStationInfo s : stationDB.getStations()) {
            if (s.getNationality().equals(Nationality.CA)) {
                setParticipation(s, Participation.EXITED_NOT_PARTICIPATING);
            } else {
                setParticipation(s, participationDecider.isParticipating(s) ? Participation.BIDDING : Participation.EXITED_NOT_PARTICIPATING);
            }
        }
    }

    public ImmutableSet<IStationInfo> getStations() {
        return ImmutableSet.copyOf(participationMap.keySet());
    }

    public void setParticipation(IStationInfo s, Participation participation) {
        final Participation previousValue = participationMap.put(s, participation);
        Preconditions.checkState(!Participation.EXITED.contains(previousValue), "Station %s switched form a terminal status %s to %s", s, previousValue, participation);
    }

    public Set<IStationInfo> getMatching(Participation participation) {
        return getMatching(ImmutableSet.of(participation));
    }

    public Set<IStationInfo> getMatching(Set<Participation> participation) {
        return participationMap.entrySet().stream()
                .filter(e -> participation.contains(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public Set<IStationInfo> getActiveStations() {
        return getMatching(Participation.ACTIVE);
    }

    // These are stations that will participate in every problem
    public Set<IStationInfo> getOnAirStations() {
        return getMatching(Participation.INACTIVE);
    }

    public Participation getParticipation(IStationInfo s) {
        return participationMap.get(s);
    }

    public boolean isActive(IStationInfo station) {
        return Participation.ACTIVE.contains(getParticipation(station));
    }

}
