package com.itx.similarproducts.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration utilizing Caffeine for high-concurrency, low-latency in-memory caching.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SIMILAR_PRODUCT_IDS_CACHE = "similarProductIds";
    public static final String PRODUCT_DETAILS_CACHE = "productDetails";

    @Value("${cache.catalog.ttl-seconds:20}")
    private long ttlSeconds;

    @Value("${cache.catalog.maximum-size:5000}")
    private long maximumSize;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            SIMILAR_PRODUCT_IDS_CACHE,
            PRODUCT_DETAILS_CACHE
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
            .maximumSize(maximumSize)
            .recordStats());
        return cacheManager;
    }
}
