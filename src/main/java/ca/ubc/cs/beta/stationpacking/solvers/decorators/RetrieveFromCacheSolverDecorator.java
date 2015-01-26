package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.database.*;
import com.codahale.metrics.Counter;
import com.codahale.metrics.RatioGauge;
import com.google.common.hash.HashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * Created by newmanne on 11/30/14.
 */
@Slf4j
public abstract class RetrieveFromCacheSolverDecorator extends ASolverDecorator {

    // METRICS
    private final static Counter cacheHits = SATFCMetrics.getRegistry().counter(name(RetrieveFromCacheSolverDecorator.class, "cache-hits"));
    private final static Counter cacheMisses = SATFCMetrics.getRegistry().counter(name(RetrieveFromCacheSolverDecorator.class, "cache-misses"));
    private final static CacheHitRatio cacheHitRatio = SATFCMetrics.getRegistry().register(name(RetrieveFromCacheSolverDecorator.class, "cache-hit-ratio"), new CacheHitRatio(cacheHits, cacheMisses));

    @RequiredArgsConstructor
    public static class CacheHitRatio extends RatioGauge {

        private final Counter cacheHits;
        private final Counter cacheMisses;

        @Override
        protected Ratio getRatio() {
            return Ratio.of(cacheHits.getCount(), cacheHits.getCount() + cacheMisses.getCount());
        }
    }

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
            cacheHits.inc();
            // TODO: think about timeout result stored in cache where you have more time and would rather try anyways
            result = cachedResult.get().getSolverResult();
        } else {
            log.info("Cache miss! Solving");
            cacheMisses.inc();
            result = super.solve(aInstance, aTerminationCriterion, aSeed);
        }
        return result;
    }

}