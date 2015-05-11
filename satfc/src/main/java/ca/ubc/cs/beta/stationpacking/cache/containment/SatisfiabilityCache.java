package ca.ubc.cs.beta.stationpacking.cache.containment;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ISatisfiabilityCache;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.utils.CacheUtils;
import containmentcache.IContainmentCache;
import containmentcache.decorators.BufferedThreadSafeCacheDecorator;

import java.util.*;
import java.util.stream.StreamSupport;

/**
 * Created by newmanne on 19/04/15.
 */
public class SatisfiabilityCache implements ISatisfiabilityCache {

    public SatisfiabilityCache(IContainmentCache<Station, ContainmentCacheSATEntry> aSATCache,IContainmentCache<Station, ContainmentCacheUNSATEntry> aUNSATCache) {
        SATCache = (BufferedThreadSafeCacheDecorator<Station, ContainmentCacheSATEntry>) aSATCache;
        UNSATCache = (BufferedThreadSafeCacheDecorator<Station, ContainmentCacheUNSATEntry>) aUNSATCache;
    }

    final BufferedThreadSafeCacheDecorator<Station, ContainmentCacheSATEntry> SATCache;
    final BufferedThreadSafeCacheDecorator<Station, ContainmentCacheUNSATEntry> UNSATCache;

    @Override
    public ContainmentCacheSATResult proveSATBySuperset(final StationPackingInstance aInstance) {
        // convert instance to bit set representation
        final BitSet bitSet = CacheUtils.toBitSet(aInstance);
        // try to narrow down the entries we have to search by only looking at supersets
        try {
            SATCache.getReadLock().lock();
            final Iterable<ContainmentCacheSATEntry> iterable = SATCache.getSupersets(new ContainmentCacheSATEntry(bitSet));
            return StreamSupport.stream(iterable.spliterator(), false)
                    /**
                     * The entry must contain at least every station in the query in order to provide a solution (hence superset)
                     * The entry should also be a solution to the problem, which it will be as long as the solution can project onto the query's domains since they come from the set of interference constraints
                     */
                    .filter(entry -> entry.isSolutionTo(aInstance))
                    .map(entry -> new ContainmentCacheSATResult(entry.getAssignmentChannelToStation(), entry.getKey()))
                    .findAny()
                    .orElse(ContainmentCacheSATResult.failure());
        } finally {
            SATCache.getReadLock().unlock();
        }
    }

    @Override
    public ContainmentCacheUNSATResult proveUNSATBySubset(final StationPackingInstance aInstance) {
        // convert instance to bit set representation
        final BitSet bitSet = CacheUtils.toBitSet(aInstance);
        // try to narrow down the entries we have to search by only looking at subsets
        try {
            UNSATCache.getReadLock().lock();
            final Iterable<ContainmentCacheUNSATEntry> iterable = UNSATCache.getSubsets(new ContainmentCacheUNSATEntry(bitSet));
            return StreamSupport.stream(iterable.spliterator(), false)
                /*
                 * The entry's stations should be a subset of the query's stations (so as to be less constrained)
                 * and each station in the entry must have larger than or equal to the corresponding station domain in the target (so as to be less constrained)
                 */
                    .filter(entry -> isSupersetOrEqualToByDomains(entry.getDomains(), aInstance.getDomains()))
                    .map(entry -> new ContainmentCacheUNSATResult(entry.getKey()))
                    .findAny()
                    .orElse(ContainmentCacheUNSATResult.failure());
        } finally {
            UNSATCache.getReadLock().unlock();
        }
    }

    @Override
    public void add(StationPackingInstance aInstance, SolverResult result, String key) {
        if (result.getResult().equals(SATResult.SAT)) {
            SATCache.add(new ContainmentCacheSATEntry(result.getAssignment(), key));
        } else if (result.getResult().equals(SATResult.UNSAT)) {
            UNSATCache.add(new ContainmentCacheUNSATEntry(aInstance.getDomains(), key));
        } else {
            throw new IllegalStateException("Tried adding a result that was neither SAT or UNSAT");
        }
    }

    // true if a's domain is a superset of b's domain
    private boolean isSupersetOrEqualToByDomains(Map<Station, Set<Integer>> a, Map<Station, Set<Integer>> b) {
        return b.entrySet().stream().allMatch(entry -> {
            final Set<Integer> integers = a.get(entry.getKey());
            return integers != null && integers.containsAll(entry.getValue());
        });
    }

    @Override
    public Iterable<ContainmentCacheSATEntry> getSupersetBySATEntry(final ContainmentCacheSATEntry e) {
        // try to narrow down the entries we have to search by only looking at supersets
        try {
            SATCache.getReadLock().lock();
            return SATCache.getSupersets(e);
        } finally {
            SATCache.getReadLock().unlock();
        }
    }

    @Override
    public Iterable<ContainmentCacheUNSATEntry> getSubsetByUNSATEntry(final ContainmentCacheUNSATEntry e) {
        // try to narrow down the entries we have to search by only looking at supersets
        try {
            UNSATCache.getReadLock().lock();
            return UNSATCache.getSubsets(e);
        } finally {
            UNSATCache.getReadLock().unlock();
        }
    }

    /**
     * removes redundant entries from this Satisfiability cache
     * @return list of prunable keys for removing the entries from Redis
     */
    @Override
    public List<ContainmentCacheSATEntry> filterSAT(){
        List<ContainmentCacheSATEntry> prunableEntries = new ArrayList<>();
        Iterable<ContainmentCacheSATEntry> satEntries = SATCache.getSets();

        satEntries.forEach(cacheEntry -> {
            Iterable<ContainmentCacheSATEntry> supersets = this.getSupersetBySATEntry(cacheEntry);
            Optional<ContainmentCacheSATEntry> foundSuperset =
                    StreamSupport.stream(supersets.spliterator(), false)
                            .filter(entry -> entry.isSupersetOf(cacheEntry))
                            .findAny();
            if (foundSuperset.isPresent()) {
                prunableEntries.add(cacheEntry);
                if (prunableEntries.size() % 2000 == 0) {
                    System.out.println("Found " + prunableEntries.size() + " prunables");
                }
            }
        });

        prunableEntries.forEach(entry -> SATCache.remove(entry));
        return prunableEntries;
    }

    /**
     * removes redundant entries from this Satisfiability cache
     * @return list of prunable keys for removing the entries from Redis
     */
    @Override
    public List<ContainmentCacheUNSATEntry> filterUNSAT(){
        List<ContainmentCacheUNSATEntry> prunableEntries = new ArrayList<>();
        Iterable<ContainmentCacheUNSATEntry> unsatEntries = UNSATCache.getSets();

        unsatEntries.forEach(cacheEntry -> {
            Iterable<ContainmentCacheUNSATEntry> subsets = this.getSubsetByUNSATEntry(cacheEntry);
            // For two UNSAT problems P and Q, if Q has less stations to pack,
            // and each station has more candidate channels, then Q is less restrictive than P
            Optional<ContainmentCacheUNSATEntry> lessRestrictiveUNSAT =
                    StreamSupport.stream(subsets.spliterator(), false)
                            .filter(entry -> entry.isLessRestrictive(cacheEntry))
                            .findAny();
            if (lessRestrictiveUNSAT.isPresent()) {
                prunableEntries.add(cacheEntry);
            }
        });

        prunableEntries.forEach(entry -> UNSATCache.remove(entry));
        return prunableEntries;
    }

}
