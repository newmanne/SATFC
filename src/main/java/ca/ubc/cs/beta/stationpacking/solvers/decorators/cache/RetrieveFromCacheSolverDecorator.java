package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import java.util.Optional;

import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics;
import ca.ubc.cs.beta.stationpacking.metrics.SATFCMetrics.SolvedByEvent;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.CacheEntry;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

/**
 * Created by newmanne on 11/30/14.
 */
@Slf4j
public class RetrieveFromCacheSolverDecorator extends ASolverDecorator {

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
    	final Watch watch = Watch.constructAutoStartWatch();
        final SolverResult result;
        Optional<CacheEntry> cachedResult = fCacher.getSolverResultFromCache(aInstance);
        if (cachedResult.isPresent()) {
            final CacheEntry cacheEntry = cachedResult.get();
            log.info("Cache hit! Result is " + cacheEntry.getSolverResult().getResult());
            SATFCMetrics.postEvent(new SATFCMetrics.SolvedByEvent(aInstance.getName(), SolvedByEvent.CACHE_HIT));
            final SolverResult cachedSolverResult = cacheEntry.getSolverResult();
            result = new SolverResult(cachedSolverResult.getResult(), watch.getElapsedTime(), cachedSolverResult.getAssignment());
        } else {
            log.info("Cache miss! Solving");
            final double preTime = watch.getElapsedTime();
            final SolverResult decoratedResult = super.solve(aInstance, aTerminationCriterion, aSeed); 
            result = SolverResult.addTime(decoratedResult, preTime);
        }
        return result;
    }

}