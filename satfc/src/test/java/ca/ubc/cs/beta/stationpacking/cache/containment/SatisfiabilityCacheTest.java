package ca.ubc.cs.beta.stationpacking.cache.containment;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.cache.ISatisfiabilityCacheFactory;
import ca.ubc.cs.beta.stationpacking.cache.RedisCacher;
import ca.ubc.cs.beta.stationpacking.cache.SatisfiabilityCacheFactory;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ISatisfiabilityCache;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.JedisShardInfo;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.stream.StreamSupport;

/**
 * Created by emily404 on 15-05-05.
 */
public class SatisfiabilityCacheTest {
    private String redisURL = "localhost";
    private int redisPort = 6379;

    private final ISatisfiabilityCacheFactory cacheFactory = new SatisfiabilityCacheFactory();
    private final RedisConnectionFactory redisCon = (RedisConnectionFactory)new JedisConnectionFactory(new JedisShardInfo(redisURL, redisPort));
    private final RedisCacher cacher = new RedisCacher(new StringRedisTemplate(redisCon));

    @Test
    public void test()
    {
        final RedisCacher.ContainmentCacheInitData containmentCacheInitData = cacher.getContainmentCacheInitData();

        List<String> prunable = new ArrayList<>();
        containmentCacheInitData.getCaches().forEach(cacheCoordinate -> {
            final List<ContainmentCacheSATEntry> SATEntries = containmentCacheInitData.getSATResults().get(cacheCoordinate);
            final List<ContainmentCacheUNSATEntry> UNSATEntries = containmentCacheInitData.getUNSATResults().get(cacheCoordinate);
            ISatisfiabilityCache cache = cacheFactory.create(SATEntries, UNSATEntries);

            SATEntries.forEach(cacheEntry -> {
                Iterable<ContainmentCacheSATEntry> supersets = cache.assignmentSuperset(cacheEntry);
                Optional<ContainmentCacheSATEntry> a = StreamSupport.stream(supersets.spliterator(), false)
                        .filter(entry -> entry.isSupersetOf(cacheEntry))
                        .findFirst();
                if(a.isPresent()){
                    prunable.add(cacheEntry.getKey());
                    System.out.println(cacheEntry.getKey()+" is subset of "+a.get().getKey());
                }
            });
            System.out.println(prunable);
            System.out.println("size:"+prunable.size());
            System.out.println("done");

        });
    }
}

