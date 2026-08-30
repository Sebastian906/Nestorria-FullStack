package com.nestorria.server.common.ai;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.nestorria.server.common.ai.dto.AiChatRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * Orquesta el streaming de chat: consume el InputStream de AiServiceClient
 * (lectura bloqueante línea a línea) y lo reenvía al SseEmitter del controller.
 *
 * Limita la concurrencia de streams activos con un Semaphore configurable
 * via app.ai-service.max-concurrent-streams.
 *
 * Preserva los tipos de evento SSE del upstream (start/token/end) tal como
 * llegan del ai-service, en lugar de envolver todo en "token".
 */
@Service
@Slf4j
public class AiChatStreamingService {

    private final AiServiceClient aiServiceClient;
    private final int maxConcurrentStreams;
    private final Semaphore streamPermits;
    private final ExecutorService streamExecutor;

    // Per-conversation state for lifecycle management (timeout/disconnect cancellation)
    private final ConcurrentHashMap<String, StreamHandle> activeStreams = new ConcurrentHashMap<>();

    private static final class StreamHandle {
        final InputStream inputStream;
        final Thread readerThread;

        StreamHandle(InputStream inputStream, Thread readerThread) {
            this.inputStream = inputStream;
            this.readerThread = readerThread;
        }

        void cancel() {
            closeQuietly(inputStream);
            if (readerThread != null) {
                readerThread.interrupt();
            }
        }
    }

    public AiChatStreamingService(AiServiceClient aiServiceClient,
            @org.springframework.beans.factory.annotation.Value("${app.ai-service.max-concurrent-streams:10}") int maxConcurrentStreams) {
        this.aiServiceClient = aiServiceClient;
        this.maxConcurrentStreams = maxConcurrentStreams;
        this.streamPermits = new Semaphore(maxConcurrentStreams);
        this.streamExecutor = new ThreadPoolExecutor(
            0, maxConcurrentStreams,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "chat-stream-" + UUID.randomUUID());
                t.setDaemon(true);
                return t;
            });
    }

    public void streamChat(AiChatRequest request, SseEmitter emitter) {
        String conversationId = request.conversationId() != null
            ? request.conversationId()
            : UUID.randomUUID().toString();

        if (!streamPermits.tryAcquire()) {
            log.warn("Chat stream rejected: max concurrent streams ({}) reached, user={}",
                maxConcurrentStreams, request.userId());
            try {
                emitter.send(SseEmitter.event()
                    .data(AiChatStreamEvent.error(
                        "Demasiadas solicitudes de chat activas. Intente más tarde.")));
                emitter.complete();
            } catch (IOException ignored) {}
            return;
        }

        streamExecutor.execute(() -> {
            InputStream inputStream = null;
            Thread self = Thread.currentThread();
            try {
                // Send start event
                emitter.send(SseEmitter.event()
                    .data(AiChatStreamEvent.start(conversationId)));

                // Open blocking stream from ai-service
                inputStream = aiServiceClient.streamChat(request);

                // Register for lifecycle management (timeout/disconnect cancellation)
                activeStreams.put(conversationId, new StreamHandle(inputStream, self));

                // Read SSE lines line-by-line (blocking, runs on daemon thread)
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            if (!line.isBlank()) {
                                String eventType = parseSseEventType(line);
                                String data = parseSseData(line);
                                if (data != null) {
                                    // Preserve original event type from upstream
                                    emitter.send(SseEmitter.event()
                                        .name(eventType != null ? eventType : "message")
                                        .data(data));
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
                // use complete() not completeWithError() — the error event
                // is already sent via SSE, and completeWithError() triggers
                // Spring's error dispatch which crashes on text/event-stream
                // content type (GlobalExceptionHandler can't serialize JSON to SSE).
                emitter.complete();
            } finally {
                activeStreams.remove(conversationId);
                closeQuietly(inputStream);
                streamPermits.release();
            }
        });

        // Lifecycle: timeout and error must cancel the blocking reader
        emitter.onTimeout(() -> {
            log.warn("Chat stream timeout for conversation {}", conversationId);
            StreamHandle handle = activeStreams.remove(conversationId);
            if (handle != null) {
                handle.cancel();
            }
            emitter.complete();
        });

        emitter.onError(e -> {
            log.warn("Chat stream error for conversation {}: {}",
                conversationId, e.getMessage());
            StreamHandle handle = activeStreams.remove(conversationId);
            if (handle != null) {
                handle.cancel();
            }
        });
    }

    /**
     * Parse SSE event type from a line.
     * Returns the event name if the line is "event: xxx", null otherwise.
     */
    private String parseSseEventType(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("event:")) {
            return trimmed.substring(6).trim();
        }
        return null;
    }

    /**
     * Parse SSE data from a line. Handles formats:
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

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (IOException ignored) {}
        }
    }
}
