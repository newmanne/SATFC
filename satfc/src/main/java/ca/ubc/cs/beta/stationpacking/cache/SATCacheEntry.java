package ca.ubc.cs.beta.stationpacking.cache;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ca.ubc.cs.beta.stationpacking.base.Station;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
* Created by newmanne on 07/10/15.
*/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SATCacheEntry implements ISATFCCacheEntry {
    private Map<String, Object> metadata;
    private Map<Integer, Set<Station>> assignment;

    @JsonIgnore
    public Set<Station> getStations() {
        Set<Station> uniqueStations = new HashSet<>();
        assignment.values().forEach(stationList -> uniqueStations.addAll(stationList));
        return uniqueStations;
    }

    @JsonIgnore
    public Map<Integer, Integer> getStationToChannel() {
        Map<Integer, Integer> stationToChannel = new HashMap<>();
        assignment.entrySet().forEach(
                entry -> entry.getValue().forEach(
                        station -> stationToChannel.put(station.getID(), entry.getKey())
                ));
        return stationToChannel;
    }
}
