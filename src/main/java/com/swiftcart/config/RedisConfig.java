package com.swiftcart.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache configuration.
 *
 * Cache invalidation strategy:
 *  - Products listing cache (TTL: 5 min) — evicted on any product write.
 *  - Individual product cache (TTL: 10 min) — evicted on update/delete.
 *  - Short TTLs ensure stale data self-heals without explicit eviction.
 *
 * All values are serialised to JSON (not Java serialisation) so cached
 * objects remain readable by other services or debug tooling.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    // Cache name constants — referenced in @Cacheable / @CacheEvict
    public static final String CACHE_PRODUCTS      = "products";
    public static final String CACHE_PRODUCT_PAGE  = "products:page";
    public static final String CACHE_CATEGORIES    = "categories";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = defaultCacheConfig();

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put(CACHE_PRODUCTS,     defaultCacheConfig().entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put(CACHE_PRODUCT_PAGE, defaultCacheConfig().entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put(CACHE_CATEGORIES,   defaultCacheConfig().entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    private RedisCacheConfiguration defaultCacheConfig() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Embed type info so deserialisation works for generic types
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)))
                .disableCachingNullValues();
    }
}
