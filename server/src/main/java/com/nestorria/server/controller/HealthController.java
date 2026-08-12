package com.nestorria.server.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.common.outbox.DeadLetterEventRepository;
import com.nestorria.server.common.outbox.OutboxEventRepository;
import com.nestorria.server.common.outbox.OutboxEventStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Gestión de estado de la API")
public class HealthController {

    private final CacheManager cacheManager;
    private final OutboxEventRepository outboxEventRepository;
    private final DeadLetterEventRepository deadLetterEventRepository;

    public HealthController(CacheManager cacheManager,
                            OutboxEventRepository outboxEventRepository,
                            DeadLetterEventRepository deadLetterEventRepository) {
        this.cacheManager = cacheManager;
        this.outboxEventRepository = outboxEventRepository;
        this.deadLetterEventRepository = deadLetterEventRepository;
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

    @Operation(summary = "Estado de las colas outbox — pendientes, procesando, DLQ")
    @GetMapping("/queues")
    public Map<String, Object> queueHealth() {
        Map<String, Object> result = new LinkedHashMap<>();

        long pending = outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
        long processing = outboxEventRepository.countByStatus(OutboxEventStatus.PROCESSING);
        long completed = outboxEventRepository.countByStatus(OutboxEventStatus.COMPLETED);
        long failed = outboxEventRepository.countByStatus(OutboxEventStatus.FAILED);
        long deadLetter = deadLetterEventRepository.count();

        result.put("pending", pending);
        result.put("processing", processing);
        result.put("completed", completed);
        result.put("failed", failed);
        result.put("deadLetter", deadLetter);
        result.put("totalActive", pending + processing);

        return result;
    }
}
