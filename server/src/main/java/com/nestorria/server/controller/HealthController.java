package com.nestorria.server.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Gestión de estado de la API")
public class HealthController {

    private final CacheManager cacheManager;

    public HealthController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Operation(summary = "Health check de la API")
    @GetMapping("/")
    public String health() {
        return "API successfully connected";
    }

    @Operation(summary = "Estado del cache — hit/miss/stats por cada cache")
    @GetMapping("/cache")
    public Map<String, Object> cacheHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cacheManager", cacheManager.getClass().getSimpleName());

        Map<String, Object> caches = new LinkedHashMap<>();
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                Object nativeCache = cache.getNativeCache();
                if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> caffeineCache) {
                    Map<String, Object> stats = new LinkedHashMap<>();
                    stats.put("size", caffeineCache.estimatedSize());
                    stats.put("hitCount", caffeineCache.stats().hitCount());
                    stats.put("missCount", caffeineCache.stats().missCount());
                    stats.put("hitRate", String.format("%.2f%%", caffeineCache.stats().hitRate() * 100));
                    stats.put("evictionCount", caffeineCache.stats().evictionCount());
                    caches.put(name, stats);
                }
            }
        }
        result.put("caches", caches);
        return result;
    }
}
