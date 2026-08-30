package com.nestorria.server.common.ai;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Rate limiter para chat: 20 mensajes por hora por usuario.
 * Usa Caffeine cache con ventana deslizante.
 * Separado del RateLimitFilter (Bucket4j) porque chat tiene
 * límites más estrictos y una ventana temporal diferente (1h vs 1min).
 */
@Component
public class AiChatRateLimiter {

    private static final int MAX_MESSAGES_PER_HOUR = 20;

    private final Cache<String, Integer> messageCounts = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofHours(1))
        .maximumSize(10_000)
        .build();

    /**
     * Verifica si el usuario puede enviar otro mensaje.
     * Lanza AiServiceException si se excede el límite.
     */
    public void checkLimit(String userId) {
        int current = messageCounts.get(userId, k -> 0);
        if (current >= MAX_MESSAGES_PER_HOUR) {
            throw new AiServiceException(
                "Límite de mensajes alcanzado. Máximo " 
                + MAX_MESSAGES_PER_HOUR + " mensajes por hora.");
        }
        messageCounts.put(userId, current + 1);
    }

    // Retorna mensajes restantes para el usuario (para headers informativos).
    public int remainingMessages(String userId) {
        int current = messageCounts.get(userId, k -> 0);
        return Math.max(0, MAX_MESSAGES_PER_HOUR - current);
    }
}
