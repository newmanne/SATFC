package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.base.Station;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
* Created by newmanne on 07/10/15.
*/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UNSATCacheEntry implements ICacher.ISATFCCacheEntry {
    private Map<String, Object> metadata;
    Map<Station, Set<Integer>> domains;
}
