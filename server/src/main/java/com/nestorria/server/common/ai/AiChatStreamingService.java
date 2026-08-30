package com.nestorria.server.common.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.nestorria.server.common.ai.dto.AiChatRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * Orquesta el streaming de chat: consume el InputStream de AiServiceClient
 * (lectura bloqueante línea a línea) y lo reenvía al SseEmitter del controller.
 * Ejecuta la lectura en un hilo separado para no bloquear el hilo del servlet.
 */
@Service
@Slf4j
public class AiChatStreamingService {

    private final AiServiceClient aiServiceClient;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "chat-stream-" + UUID.randomUUID());
        t.setDaemon(true);
        return t;
    });

    public AiChatStreamingService(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    public void streamChat(AiChatRequest request, SseEmitter emitter) {
        String conversationId = request.conversationId() != null
            ? request.conversationId()
            : UUID.randomUUID().toString();

        streamExecutor.execute(() -> {
            InputStream inputStream = null;
            try {
                // Send start event
                emitter.send(SseEmitter.event()
                    .data(AiChatStreamEvent.start(conversationId)));

                // Open blocking stream from ai-service
                inputStream = aiServiceClient.streamChat(request);

                // Read SSE lines line-by-line (blocking, runs on daemon thread)
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            if (!line.isBlank()) {
                                String data = parseSseData(line);
                                if (data != null) {
                                    emitter.send(SseEmitter.event()
                                        .data(AiChatStreamEvent.token(data)));
                                }
                            }
                        } catch (IOException e) {
                            // Client disconnected — stop reading
                            log.debug("Client disconnected during stream: {}", e.getMessage());
                            return;
                        }
                    }
                }

                // Stream finished normally — send end event
                try {
                    emitter.send(SseEmitter.event()
                        .data(AiChatStreamEvent.end(conversationId, List.of())));
                    emitter.complete();
                } catch (IOException e) {
                    log.debug("Client disconnected at stream end: {}", e.getMessage());
                }

            } catch (Exception e) {
                log.warn("Stream error for user {}: {}",
                    request.userId(), e.getMessage());
                try {
                    emitter.send(SseEmitter.event()
                        .data(AiChatStreamEvent.error(
                            "Error en el servicio de IA")));
                } catch (IOException ignored) {
                    // Client already disconnected
                }
                emitter.completeWithError(e);
            } finally {
                // Ensure stream is closed even on error
                if (inputStream != null) {
                    try { inputStream.close(); } catch (IOException ignored) {}
                }
            }
        });

        emitter.onTimeout(() -> {
            log.warn("Chat stream timeout for conversation {}", conversationId);
            emitter.complete();
        });

        emitter.onError(e -> {
            log.warn("Chat stream error for conversation {}: {}",
                conversationId, e.getMessage());
        });
    }

    /**
     * Parse SSE data lines. Handles formats:
     * - "data: {...}" → returns "{...}"
     * - "{...}" (raw JSON) → returns "{...}"
     * - Lines starting with "event:", "id:", "retry:" → returns null (skip)
     */
    private String parseSseData(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.startsWith("data:")) {
            return trimmed.substring(5).trim();
        }
        // If it looks like JSON, pass through
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        return null;
    }
}
