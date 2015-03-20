package ca.ubc.cs.beta.stationpacking.cache;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import ca.ubc.cs.beta.stationpacking.base.Station;
import com.sun.jna.win32.StdCallFunctionMapper;
import lombok.Data;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Created by newmanne on 05/12/14.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
public class CacheEntry {

    private final Date cacheDate;
    private final String name;

    public static class SATCacheEntry extends CacheEntry {
        private final Map<Integer, Set<Station>> assignment;
    }

    public static class UNSATCacheEntry extends SATCacheEntry {

        private final Map<Station, Set<Integer>> domains;

        public UNSATCacheEntry(SolverResult solverResult, Date cacheDate, String name, Map<Station, Set<Integer>> domains) {
            super(solverResult, cacheDate, name);
            this.domains = domains;
        }
    }

}
