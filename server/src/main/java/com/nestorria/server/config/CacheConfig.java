package com.nestorria.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

@Configuration
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "propertyListings", "ownerProperties", "ratingAggregates", "unreadCount"
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats());
        return cacheManager;
    }

    @PostConstruct
    void logStartup() {
        log.info("[CACHE] CacheConfig initialized — cache names: propertyListings, ownerProperties, ratingAggregates, unreadCount");
    }
}
