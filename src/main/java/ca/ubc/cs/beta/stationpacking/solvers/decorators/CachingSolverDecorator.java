package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCCachingParameters;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.Data;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
                // TODO: rehash! - and if that fails, somehow go to the else statement...
            }
            if (cacheEntry.getSolverResult().getResult() == SATResult.TIMEOUT && cacheEntry.getSolverResult().getRuntime() < satfcCachingParameters.minTimeToTrustCache && aTerminationCriterion.getRemainingTime() - 1.0 > cacheEntry.getSolverResult().getRuntime()) {
                // TODO: a cached result says timeout, but you have way more time and would prefer to try recomputing it
            }
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
    public static class CacheEntry {
        private final SolverResult solverResult;
        private final Map<Station, Set<Integer>> domains;
    }
}