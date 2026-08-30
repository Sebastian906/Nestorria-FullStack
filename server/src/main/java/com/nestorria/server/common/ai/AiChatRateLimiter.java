package com.nestorria.server.common.ai;

import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Rate limiter para chat: 20 mensajes por hora por usuario.
 * Usa Caffeine cache con ventana deslizante.
 * Separado del RateLimitFilter (Bucket4j) porque chat tiene
 * límites más estrictos y una ventana temporal diferente (1h vs 1min).
 *
 * La operación checkLimit es atómica: validate + increment ocurren
 * dentro de un solo compute, evitando race conditions entre threads
 * concurrentes para el mismo usuario.
 */
@Component
public class AiChatRateLimiter {

    private static final int MAX_MESSAGES_PER_HOUR = 20;

    private final Cache<String, Integer> messageCounts = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofHours(1))
        .maximumSize(10_000)
        .build();

    /**
     * Verifica y registra un mensaje para el usuario.
     * Operación atómica: valida el límite e incrementa en un solo compute.
     * Lanza AiServiceException si se excede el límite.
     */
    public void checkLimit(String userId) {
        Integer newValue = messageCounts.asMap().compute(userId, (key, current) -> {
            int count = current != null ? current : 0;
            if (count >= MAX_MESSAGES_PER_HOUR) {
                // Return null to not store — but we need to throw before that.
                // Throw inside compute: Caffeine propagates it to the caller.
                throw new AiServiceException(
                    "Límite de mensajes alcanzado. Máximo "
                    + MAX_MESSAGES_PER_HOUR + " mensajes por hora.");
            }
            return count + 1;
        });
        // If compute returned normally, newValue is count+1 (allowed).
    }

    /**
     * Retorna mensajes restantes para el usuario (para headers informativos).
     */
    public int remainingMessages(String userId) {
        Map<String, Integer> snapshot = messageCounts.asMap();
        int current = snapshot.getOrDefault(userId, 0);
        return Math.max(0, MAX_MESSAGES_PER_HOUR - current);
    }
}
