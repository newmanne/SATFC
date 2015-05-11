package ca.ubc.cs.beta.stationpacking.cache;

import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheUNSATEntry;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ISatisfiabilityCache;

import java.util.Collection;

/**
* Created by newmanne on 22/04/15.
*/
public interface ISatisfiabilityCacheFactory {
    ISatisfiabilityCache create(Collection<ContainmentCacheSATEntry> SATEntries, Collection<ContainmentCacheUNSATEntry> UNSATEntries);
}
