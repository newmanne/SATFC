package ca.ubc.cs.beta.stationpacking.solvers.database;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ACachingSolverDecorator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 05/12/14.
 */
@JsonDeserialize(using = CacheEntry.CacheEntryDeserializer.class)
@Data
public class CacheEntry {

    private final SolverResult solverResult;
    private final Map<Station, Set<Integer>> domains;
    private final Date cacheDate;

    public static class CacheEntryDeserializer extends JsonDeserializer<CacheEntry> {

        @Override
        public CacheEntry deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            final CacheEntryJson cacheEntryJson = jp.readValueAs(CacheEntryJson.class);
            final Map<Station, Set<Integer>> collect = cacheEntryJson.getDomains().entrySet()
                    .stream()
                    .collect(Collectors.toMap(e -> new Station(e.getKey()), Map.Entry::getValue));
            return new CacheEntry(cacheEntryJson.getSolverResult(), collect, cacheEntryJson.getCacheDate());
        }

    }

    @Data
    private static class CacheEntryJson {
        private SolverResult solverResult;
        private Map<Integer, Set<Integer>> domains;
        private Date cacheDate;
    }

}
