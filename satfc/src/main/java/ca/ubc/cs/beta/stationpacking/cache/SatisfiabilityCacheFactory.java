package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheUNSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.SatisfiabilityCache;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.IContainmentCache;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ISatisfiabilityCache;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.bitset.SimpleBitSetCache;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.decorators.ThreadSafeContainmentCacheDecorator;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;

import java.util.Collection;
import java.util.Set;
import java.util.stream.IntStream;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableSet;

/**
* Created by newmanne on 22/04/15.
*/
public class SatisfiabilityCacheFactory implements ISatisfiabilityCacheFactory {

    final Set<Station> universe = IntStream.rangeClosed(1, StationPackingUtils.N_STATIONS).mapToObj(Station::new).collect(toImmutableSet());

    @Override
    public ISatisfiabilityCache create(Collection<ContainmentCacheSATEntry> SATEntries, Collection<ContainmentCacheUNSATEntry> UNSATEntries) {
        final IContainmentCache<Station, ContainmentCacheSATEntry> SATCache = ThreadSafeContainmentCacheDecorator.makeThreadSafe(new SimpleBitSetCache<>(universe));
        SATCache.addAll(SATEntries);
        final IContainmentCache<Station, ContainmentCacheUNSATEntry> UNSATCache = ThreadSafeContainmentCacheDecorator.makeThreadSafe(new SimpleBitSetCache<>(universe));
        UNSATCache.addAll(UNSATEntries);
        return new SatisfiabilityCache(SATCache, UNSATCache);
    }

}
