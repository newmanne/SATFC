package ca.ubc.cs.beta.stationpacking.cache.containment;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.ISatisfiabilityCacheFactory;
import ca.ubc.cs.beta.stationpacking.cache.RedisCacher;
import ca.ubc.cs.beta.stationpacking.cache.SatisfiabilityCacheFactory;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ISatisfiabilityCache;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.utils.CacheUtils;
import containmentcache.IContainmentCache;
import containmentcache.decorators.BufferedThreadSafeContainmentCacheDecorator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.clients.jedis.JedisShardInfo;

import java.util.*;
import java.util.stream.StreamSupport;

/**
 * Created by newmanne on 19/04/15.
 */
public class SatisfiabilityCache implements ISatisfiabilityCache {

    public SatisfiabilityCache(IContainmentCache<Station, ContainmentCacheSATEntry> aSATCache,IContainmentCache<Station, ContainmentCacheUNSATEntry> aUNSATCache) {
        SATCache = (BufferedThreadSafeContainmentCacheDecorator<Station, ContainmentCacheSATEntry>) aSATCache;
        UNSATCache = (BufferedThreadSafeContainmentCacheDecorator<Station, ContainmentCacheUNSATEntry>) aUNSATCache;
    }

    final BufferedThreadSafeContainmentCacheDecorator<Station, ContainmentCacheSATEntry> SATCache;
    final BufferedThreadSafeContainmentCacheDecorator<Station, ContainmentCacheUNSATEntry> UNSATCache;

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
    public Iterable<ContainmentCacheSATEntry> assignmentSuperset(final ContainmentCacheSATEntry e) {
        // try to narrow down the entries we have to search by only looking at supersets
        try {
            SATCache.getReadLock().lock();
            return SATCache.getSupersets(e);
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

    @Override
    public void filter(String redisURL, int redisPort){

        final ISatisfiabilityCacheFactory cacheFactory = new SatisfiabilityCacheFactory();
        final RedisConnectionFactory redisCon = new JedisConnectionFactory(new JedisShardInfo(redisURL, redisPort));
        final RedisCacher cacher = new RedisCacher(new StringRedisTemplate(redisCon));
        final RedisCacher.ContainmentCacheInitData containmentCacheInitData = cacher.getContainmentCacheInitData();
        List<String> prunable = new ArrayList<>();

        containmentCacheInitData.getCaches().forEach(cacheCoordinate -> {
            final List<ContainmentCacheSATEntry> SATEntries = containmentCacheInitData.getSATResults().get(cacheCoordinate);
            final List<ContainmentCacheUNSATEntry> UNSATEntries = containmentCacheInitData.getUNSATResults().get(cacheCoordinate);
            ISatisfiabilityCache cache = cacheFactory.create(SATEntries, UNSATEntries);

            SATEntries.forEach(cacheEntry -> {
                Iterable<ContainmentCacheSATEntry> supersets = cache.assignmentSuperset(cacheEntry);
                Optional<ContainmentCacheSATEntry> foundSuperset =
                        StreamSupport.stream(supersets.spliterator(), false)
                                .filter(entry -> entry.isSupersetOf(cacheEntry))
                                .findFirst();
                if (foundSuperset.isPresent()) {
                    SATCache.remove(cacheEntry);
                    prunable.add(cacheEntry.getKey());
                }
            });
        });

        //remove prunables from redis
        cacher.removeKeys(prunable);

    }

    // true if a's domain is a superset of b's domain
    private boolean isSupersetOrEqualToByDomains(Map<Station, Set<Integer>> a, Map<Station, Set<Integer>> b) {
        return b.entrySet().stream().allMatch(entry -> {
            final Set<Integer> integers = a.get(entry.getKey());
            return integers != null && integers.containsAll(entry.getValue());
        });
    }

}
