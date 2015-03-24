package ca.ubc.cs.beta.stationpacking.cache;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import ca.ubc.cs.beta.stationpacking.base.Station;
import com.sun.jna.win32.StdCallFunctionMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Created by newmanne on 05/12/14.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheEntry {

    private Date cacheDate;
    private Map<String, Object> metadata;

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class SATCacheEntry extends CacheEntry {
        private Map<Integer, Set<Station>> assignment;

        public SATCacheEntry(Date date, Map<String, Object> metadata, Map<Integer, Set<Station>> assignment) {
            super(date, metadata);
            this.assignment = assignment;
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class UNSATCacheEntry extends CacheEntry {
        private Map<Station, Set<Integer>> domains;

        public UNSATCacheEntry(Date date, Map<String, Object> metadata, Map<Station, Set<Integer>> domains) {
            super(date, metadata);
            this.domains = domains;
        }
    }

}