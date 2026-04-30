package com.team28.booking.calendar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        RedisCacheConfiguration baseline = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(name -> name + "::")
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put("calendar-service::S4-F1", baseline.entryTtl(Duration.ofMinutes(5)));
        perCache.put("calendar-service::S4-F3", baseline.entryTtl(Duration.ofMinutes(10)));
        perCache.put("calendar-service::S4-F5", baseline.entryTtl(Duration.ofMinutes(5)));
        perCache.put("calendar-service::S4-F6", baseline.entryTtl(Duration.ofMinutes(10)));
        perCache.put("calendar-service::S4-F8", baseline.entryTtl(Duration.ofMinutes(15)));
        perCache.put("calendar-service::S4-F9", baseline.entryTtl(Duration.ofMinutes(10)));
        perCache.put("calendar-service::S4-F10", baseline.entryTtl(Duration.ofMinutes(10)));
        perCache.put("calendar-service::S4-F12", baseline.entryTtl(Duration.ofMinutes(10)));
        perCache.put("calendar-service::time-slot", baseline.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(cf)
                .cacheDefaults(baseline.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache GET error on cache={} key={}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache PUT error on cache={} key={}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache EVICT error on cache={} key={}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache CLEAR error on cache={}: {}", cache.getName(), e.getMessage());
            }
        };
    }
}
