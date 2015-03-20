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

/**
 * Created by newmanne on 05/12/14.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
@AllArgsConstructor
public class CacheEntry {

    private Date cacheDate;
    private String name;

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SATCacheEntry extends CacheEntry {
        private Map<Integer, Set<Station>> assignment;

        public SATCacheEntry(Date date, String name, Map<Integer, Set<Station>> assignment) {
            super(date, name);
            this.assignment = assignment;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UNSATCacheEntry extends CacheEntry {
        private Map<Station, Set<Integer>> domains;

        public UNSATCacheEntry(Date date, String name, Map<Station, Set<Integer>> domains) {
            super(date, name);
            this.domains = domains;
        }
    }

}