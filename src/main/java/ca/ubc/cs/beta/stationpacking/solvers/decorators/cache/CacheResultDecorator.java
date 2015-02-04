package ca.ubc.cs.beta.stationpacking.solvers.decorators.cache;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.CacheEntry;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.decorators.ASolverDecorator;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * Created by newmanne on 1/25/15.
 */
@Slf4j
public class CacheResultDecorator extends ASolverDecorator {

    private final ICacher fCacher;
    private final CachingStrategy cachingStrategy;

    /**
     * @param aSolver - decorated ISolver.
     */
    public CacheResultDecorator(ISolver aSolver, ICacher aCacher, CachingStrategy cachingStrategy) {
        super(aSolver);
        fCacher = aCacher;
        this.cachingStrategy = cachingStrategy;
    }

    public CacheResultDecorator(ISolver aSolver, ICacher aCacher) {
        this(aSolver, aCacher, new CachingStrategy() {});
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final SolverResult result = fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
        if (cachingStrategy.shouldCache(result)) {
        	final CacheEntry cacheEntry = new CacheEntry(result, aInstance.getDomains(), new Date(), aInstance.getName());
            fCacher.cacheResult(aInstance, cacheEntry);
        }
        return result;
    }

    public interface CachingStrategy {
        final static ImmutableList<SATResult> fCacheableResults = ImmutableList.of(SATResult.SAT, SATResult.UNSAT);

        default boolean shouldCache(SolverResult result) {
            return fCacheableResults.contains(result.getResult());
        }
    }

}
