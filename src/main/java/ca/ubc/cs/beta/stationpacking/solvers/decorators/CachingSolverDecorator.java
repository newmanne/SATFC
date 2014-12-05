package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCCachingParameters;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 11/30/14.
 */
public abstract class CachingSolverDecorator extends ASolverDecorator {

    private final SATFCCachingParameters satfcCachingParameters;
    // a hash representation of the stations / interference constraints
    private final String fGraphHash;
    // a list of results that can make their way to the database
    private final static ImmutableList<SATResult> fCacheableResults = ImmutableList.of(SATResult.SAT, SATResult.UNSAT, SATResult.TIMEOUT);
    // hashing function
    private static final HashFunction fHashFuction = Hashing.murmur3_128();

    /**
     * @param aSolver - decorated ISolver.
     */
    public CachingSolverDecorator(ISolver aSolver, SATFCCachingParameters satfcCachingParameters, String aGraphHash) {
        super(aSolver);
        this.satfcCachingParameters = satfcCachingParameters;
        this.fGraphHash = aGraphHash;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final SolverResult result;
        final Optional<CacheEntry> cachedResult = getSolverResultFromCache(aInstance);
        if (cachedResult.isPresent()) {
            final CacheEntry cacheEntry = cachedResult.get();
            if (!cacheEntry.getDomains().equals(aInstance.getDomains())) {
                throw new RuntimeException("You've got a hash collision! Time to think about this properly");
            }
            // TODO: think about timeout result stored in cache where you have more time and would rather try anyways
            result = cachedResult.get().getSolverResult();
        } else {
            result = super.solve(aInstance, aTerminationCriterion, aSeed);
            if (shouldCache(result)) {
                cacheResult(new CacheEntry(result, aInstance.getDomains()));
            }
        }
        return result;
    }

    private boolean shouldCache(SolverResult result) {
        // No point in caching a killed result or one that we can compute faster than a db lookup
        return fCacheableResults.contains(result.getResult()) && result.getRuntime() > satfcCachingParameters.minTimeToCache;
    }

    protected HashCode hash(StationPackingInstance aInstance) {
        return fHashFuction.newHasher()
                .putString(aInstance.getHashString(), Charsets.UTF_8)
                .putString(fGraphHash, Charsets.UTF_8)
                .hash();
    }

    protected HashCode hash(Map<Station, Set<Integer>> aDomains) {
        return hash(new StationPackingInstance(aDomains));
    }

    protected abstract void cacheResult(CacheEntry entry);

    protected abstract Optional<CacheEntry> getSolverResultFromCache(StationPackingInstance aInstance);

    @Data
    @JsonDeserialize(using = CacheEntryDeserializer.class)
    public static class CacheEntry {
        private final SolverResult solverResult;
        private final Map<Station, Set<Integer>> domains;
    }

    public static class CacheEntryDeserializer extends JsonDeserializer<CacheEntry> {

        @Override
        public CacheEntry deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            final CacheEntryJson cacheEntryJson = jp.readValueAs(CacheEntryJson.class);
            final Map<Station, Set<Integer>> collect = cacheEntryJson.getDomains().entrySet().stream().collect(Collectors.toMap(e -> new Station(e.getKey()), e -> e.getValue()));
            return new CacheEntry(cacheEntryJson.getSolverResult(), collect);
        }
    }

    @Data
    public static class CacheEntryJson {
        private SolverResult solverResult;
        private Map<Integer, Set<Integer>> domains;
    }

}