package ca.ubc.cs.beta.stationpacking.cache.containment;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.cache.ISatisfiabilityCacheFactory;
import ca.ubc.cs.beta.stationpacking.cache.RedisCacher;
import ca.ubc.cs.beta.stationpacking.cache.SatisfiabilityCacheFactory;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ISatisfiabilityCache;
import com.google.common.collect.ImmutableList;
import jdk.nashorn.internal.ir.annotations.Ignore;
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
    final RedisCacher.ContainmentCacheInitData containmentCacheInitData = cacher.getContainmentCacheInitData();

//    @Test
//    public void filterSATTest()
//    {
//        containmentCacheInitData.getCaches().forEach(cacheCoordinate -> {
//            final List<ContainmentCacheSATEntry> SATEntries = containmentCacheInitData.getSATResults().get(cacheCoordinate);
//            final List<ContainmentCacheUNSATEntry> UNSATEntries = containmentCacheInitData.getUNSATResults().get(cacheCoordinate);
//            ISatisfiabilityCache cache = cacheFactory.create(SATEntries, UNSATEntries);
//            List<String> prunables = cache.filterSAT();
//            cacher.deleteByKeys(prunables);
//        });
//    }

    @Test
    public void filterUNSATTest()
    {
        containmentCacheInitData.getCaches().forEach(cacheCoordinate -> {
            final List<ContainmentCacheSATEntry> SATEntries = containmentCacheInitData.getSATResults().get(cacheCoordinate);
            final List<ContainmentCacheUNSATEntry> UNSATEntries = containmentCacheInitData.getUNSATResults().get(cacheCoordinate);
            ISatisfiabilityCache cache = cacheFactory.create(SATEntries, UNSATEntries);
            List<String> prunables = cache.filterUNSAT();
            System.out.println(prunables.size());
            System.out.println(prunables);
            //cacher.deleteByKeys(prunables);
        });
    }

}

