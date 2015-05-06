package ca.ubc.cs.beta.stationpacking.cache.containment;

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

import java.util.List;

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
        System.out.println(containmentCacheInitData.getCaches().size());
        System.out.println("size");

        containmentCacheInitData.getCaches().forEach(cacheCoordinate -> {
            final List<ContainmentCacheSATEntry> SATEntries = containmentCacheInitData.getSATResults().get(cacheCoordinate);
            final List<ContainmentCacheUNSATEntry> UNSATEntries = containmentCacheInitData.getUNSATResults().get(cacheCoordinate);
            ISatisfiabilityCache cache = cacheFactory.create(SATEntries, UNSATEntries);
            System.out.println("first entry: "+SATEntries.get(0));
            Iterable<ContainmentCacheSATEntry> supersets = cache.assignmentSuperset(SATEntries.get(0));
            supersets.forEach();
        });
    }
}
