package ca.ubc.cs.beta.stationpacking.cache;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheUNSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.SatisfiabilityCache;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ISatisfiabilityCache;
import ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors;
import containmentcache.IContainmentCache;
import containmentcache.ILockableContainmentCache;
import containmentcache.bitset.simple.SimpleBitSetCache;
import containmentcache.decorators.BufferedThreadSafeCacheDecorator;

/**
* Created by newmanne on 22/04/15.
*/
@Slf4j
public class SatisfiabilityCacheFactory implements ISatisfiabilityCacheFactory {

    private static final int SAT_BUFFER_SIZE = 100;
    private static final int UNSAT_BUFFER_SIZE = 3;
    private final Set<Station> universe;

    public SatisfiabilityCacheFactory(List<String> stationIds) {
        universe = stationIds.stream().map(Integer::parseInt).sorted().map(Station::new).collect(GuavaCollectors.toImmutableSet());
    }

    @Override
    public ISatisfiabilityCache create(Collection<ContainmentCacheSATEntry> SATEntries, Collection<ContainmentCacheUNSATEntry> UNSATEntries) {
        final IContainmentCache<Station, ContainmentCacheSATEntry> SATCache = BufferedThreadSafeCacheDecorator.makeBufferedThreadSafe(new SimpleBitSetCache<>(universe), SAT_BUFFER_SIZE);
        SATCache.addAll(SATEntries);
        final IContainmentCache<Station, ContainmentCacheUNSATEntry> UNSATCache = BufferedThreadSafeCacheDecorator.makeBufferedThreadSafe(new SimpleBitSetCache<>(universe), UNSAT_BUFFER_SIZE);
        UNSATCache.addAll(UNSATEntries);
        return new SatisfiabilityCache((ILockableContainmentCache<Station, ContainmentCacheSATEntry>)SATCache, (ILockableContainmentCache<Station, ContainmentCacheUNSATEntry>)UNSATCache);
    }

}
