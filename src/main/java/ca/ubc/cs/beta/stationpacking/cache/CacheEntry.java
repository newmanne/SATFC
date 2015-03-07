package ca.ubc.cs.beta.stationpacking.cache;

import java.io.IOException;
import java.util.Date;

import lombok.Data;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Created by newmanne on 05/12/14.
 */
@JsonDeserialize(using = CacheEntry.CacheEntryDeserializer.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
public class CacheEntry {

    private final SolverResult solverResult;
    private final Date cacheDate;
    private final String name;

    public static class CacheEntryDeserializer extends JsonDeserializer<CacheEntry> {

        @Override
        public CacheEntry deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            final CacheEntryJson cacheEntryJson = jp.readValueAs(CacheEntryJson.class);
            return new CacheEntry(cacheEntryJson.getSolverResult(), cacheEntryJson.getCacheDate(), cacheEntryJson.getName());
        }

    }

    @Data
    private static class CacheEntryJson {
        private SolverResult solverResult;
        private Date cacheDate;
        private String name;
    }

}
