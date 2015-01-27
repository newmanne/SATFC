package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.database.CacheEntry;
import ca.ubc.cs.beta.stationpacking.database.ICacher;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

/**
 * Created by newmanne on 11/30/14.
 */
@Slf4j
public class RetrieveFromCacheSolverDecorator extends ASolverDecorator {

    // METRICS
    private static long cacheHits;
    private static long cacheMisses;

    private final ICacher fCacher;

    /**
     * @param aSolver - decorated ISolver.
     */
    public RetrieveFromCacheSolverDecorator(ISolver aSolver, ICacher aCacher) {
        super(aSolver);
        fCacher = aCacher;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final SolverResult result;
        Optional<CacheEntry> cachedResult = fCacher.getSolverResultFromCache(aInstance);
        if (cachedResult.isPresent()) {
            final CacheEntry cacheEntry = cachedResult.get();
            log.info("Cache hit! Result is " + cacheEntry.getSolverResult().getResult());
            cacheHits++;
            // TODO: think about timeout result stored in cache where you have more time and would rather try anyways
            result = cachedResult.get().getSolverResult();
        } else {
            log.info("Cache miss! Solving");
            cacheMisses++;
            result = super.solve(aInstance, aTerminationCriterion, aSeed);
        }
        return result;
    }

}