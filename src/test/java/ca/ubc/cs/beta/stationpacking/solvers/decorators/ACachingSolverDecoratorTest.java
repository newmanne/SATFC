package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import ca.ubc.cs.beta.stationpacking.database.CacheEntry;
import ca.ubc.cs.beta.stationpacking.execution.parameters.SATFCCachingParameters;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import com.beust.jcommander.internal.Maps;
import com.google.common.hash.HashCode;

import java.util.Map;
import java.util.Optional;

public class ACachingSolverDecoratorTest {

    public static class MapCachingSolverDecorator extends ACachingSolverDecorator {

        private final Map<String, CacheEntry> cache = Maps.newHashMap();

        public MapCachingSolverDecorator(ISolver aSolver, SATFCCachingParameters satfcCachingParameters, String aGraphHash) {
            super(aSolver, satfcCachingParameters, aGraphHash);
        }

        @Override
        protected void cacheResult(HashCode hash, CacheEntry entry) {
            cache.put(hash.toString(), entry);
        }

        @Override
        protected Optional<CacheEntry> getSolverResultFromCache(HashCode hash) {
            return Optional.of(cache.get(hash.toString()));
        }
    }

}