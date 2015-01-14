package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCCachingParameters;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.database.*;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Created by newmanne on 11/30/14.
 */
@Slf4j
public abstract class ACachingSolverDecorator extends ASolverDecorator {

    private static final double MIN_TIME_TO_CACHE = 1.0;
	// a representation of the stations / interference constraints
    private final String fInterferenceName;
    // a list of results that can make their way to the database
    private final static ImmutableList<SATResult> fCacheableResults = ImmutableList.of(SATResult.SAT, SATResult.UNSAT, SATResult.TIMEOUT);
    // hashing function
    private static final HashFunction fHashFuction = Hashing.murmur3_128();

    /**
     * @param aSolver - decorated ISolver.
     */
    public ACachingSolverDecorator(ISolver aSolver, String aGraphHash) {
        super(aSolver);
        this.fInterferenceName = aGraphHash;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final SolverResult result;
        HashCode hash = hash(aInstance);
        Optional<CacheEntry> cachedResult = getSolverResultFromCache(hash);
        while (cachedResult.isPresent() && !cachedResult.get().getDomains().equals(aInstance.getDomains())) {
            log.debug("Hash " + hash + " has a collision, rehashing");
            hash = rehash(hash);
            cachedResult = getSolverResultFromCache(hash);
        }
        if (cachedResult.isPresent()) {
            final CacheEntry cacheEntry = cachedResult.get();
            log.info("Cache hit! Result is " + cacheEntry.getSolverResult().getResult());
            // TODO: think about timeout result stored in cache where you have more time and would rather try anyways
            result = cachedResult.get().getSolverResult();
        } else {
            log.info("Cache miss! Solving");
            result = super.solve(aInstance, aTerminationCriterion, aSeed);
            if (shouldCache(result)) {
                final CacheEntry cacheEntry = new CacheEntry(result, aInstance.getDomains(), new Date(), fInterferenceName);
                cacheResult(hash, cacheEntry);
            }
        }
        return result;
    }

    private boolean shouldCache(SolverResult result) {
        // No point in caching a killed result or one that we can compute faster than a db lookup
        return fCacheableResults.contains(result.getResult()) && result.getRuntime() > MIN_TIME_TO_CACHE;
    }

    protected HashCode hash(StationPackingInstance aInstance) {
        return fHashFuction.newHasher()
                .putString(aInstance.toString(), Charsets.UTF_8)
                .putString(fInterferenceName, Charsets.UTF_8)
                .hash();
    }

    private HashCode rehash(HashCode hash) {
        return fHashFuction.newHasher().putString(hash.toString(), Charsets.UTF_8).hash();
    }

    protected HashCode hash(Map<Station, Set<Integer>> aDomains) {
        return hash(new StationPackingInstance(aDomains));
    }

    protected abstract void cacheResult(HashCode hash, CacheEntry entry);

    protected abstract Optional<CacheEntry> getSolverResultFromCache(HashCode hash);

}