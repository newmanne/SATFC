package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.database.CacheEntry;
import ca.ubc.cs.beta.stationpacking.database.ICacher;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import com.google.common.collect.ImmutableList;

import java.util.Date;

/**
 * Created by newmanne on 1/25/15.
 */
public class CacheResultDecorator extends ASolverDecorator {

    private static final double MIN_TIME_TO_CACHE = 1.0;
    private final static ImmutableList<SATResult> fCacheableResults = ImmutableList.of(SATResult.SAT, SATResult.UNSAT, SATResult.TIMEOUT);
    private final ICacher fCacher;

    /**
     * @param aSolver - decorated ISolver.
     */
    public CacheResultDecorator(ISolver aSolver, ICacher aCacher) {
        super(aSolver);
        fCacher = aCacher;
    }

    @Override
    public SolverResult solve(StationPackingInstance aInstance, ITerminationCriterion aTerminationCriterion, long aSeed) {
        final SolverResult result = fDecoratedSolver.solve(aInstance, aTerminationCriterion, aSeed);
        if (shouldCache(result)) {
            final CacheEntry cacheEntry = new CacheEntry(result, aInstance.getDomains(), new Date());
            fCacher.cacheResult(hash, cacheEntry);
        }
        return super.solve(aInstance, aTerminationCriterion, aSeed);
    }

    private boolean shouldCache(SolverResult result) {
        // No point in caching a killed result or one that we can compute faster than a db lookup
        return fCacheableResults.contains(result.getResult()) && result.getRuntime() > MIN_TIME_TO_CACHE;
    }

}
