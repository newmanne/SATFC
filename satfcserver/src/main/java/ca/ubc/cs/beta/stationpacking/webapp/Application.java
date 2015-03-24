package ca.ubc.cs.beta.stationpacking.webapp;

import ca.ubc.cs.beta.stationpacking.cache.ContainmentCache;
import ca.ubc.cs.beta.stationpacking.cache.ICacheLocator;
import ca.ubc.cs.beta.stationpacking.cache.ICacher;
import ca.ubc.cs.beta.stationpacking.cache.RedisCacher;
import ca.ubc.cs.beta.stationpacking.cache.RedisCacher.ContainmentCacheInitData;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.RedisHealthIndicator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import redis.clients.jedis.JedisShardInfo;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by newmanne on 23/03/15.
 */
@Slf4j
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    // These will be set by command line properties e.g. --redis.port=8080
    @Value("${redis.host:localhost}")
    String redisURL;
    @Value("${redis.port:6379}")
    int redisPort;

    @Bean
    MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        final MappingJackson2HttpMessageConverter mappingJacksonHttpMessageConverter = new MappingJackson2HttpMessageConverter();
        final ObjectMapper mapper = JSONUtils.getMapper();
        mappingJacksonHttpMessageConverter.setObjectMapper(mapper);
        return mappingJacksonHttpMessageConverter;
    }

    @Bean
    RedisConnectionFactory redisConnectionFactory() {
        return new JedisConnectionFactory(new JedisShardInfo(redisURL, redisPort));
    }

    @Bean
    RedisCacher cacher() {
        return new RedisCacher(redisTemplate());
    }

    @Bean
    StringRedisTemplate redisTemplate() {
        return new StringRedisTemplate(redisConnectionFactory());
    }

    @Bean
    ICacheLocator containmentCache() {
        final ContainmentCacheInitData subsetCacheData = cacher().getContainmentCacheInitData();
        final ConcurrentMap<ICacher.CacheCoordinate, ContainmentCache> caches = new ConcurrentHashMap<>();
        subsetCacheData.getCaches().forEach(cacheCoordinate -> {
            caches.put(cacheCoordinate, new ContainmentCache(subsetCacheData.getSATResults().get(cacheCoordinate), subsetCacheData.getUNSATResults().get(cacheCoordinate)));
        });
        return coordinate -> {
            if (!caches.containsKey(coordinate)) {
                log.warn("No information known about cache: " + coordinate);
            }
            return Optional.ofNullable(caches.get(coordinate));
        };
    }


}
