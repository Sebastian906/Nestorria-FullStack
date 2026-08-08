package com.nestorria.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration is driven entirely by Spring Boot auto-configuration
 * via {@code spring.cache.type} and {@code spring.cache.caffeine.spec} in
 * application.properties. No custom {@code CacheManager} bean is defined
 * so that production and test profiles can control cache parameters through
 * their respective property files.
 */
@Configuration
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    // Intentionally no @Bean CacheManager — let Spring Boot auto-configure
    // from spring.cache.caffeine.spec per profile (prod=1000, test=100).
}
