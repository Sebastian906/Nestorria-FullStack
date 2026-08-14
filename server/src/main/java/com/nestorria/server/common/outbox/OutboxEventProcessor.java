package com.nestorria.server.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxEventProcessor {

    private static final long MAX_BACKOFF_MS = 60_000; // 60 seconds
    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxEventRepository outboxRepository;
    private final DeadLetterEventRepository deadLetterRepository;
    private final List<EventHandler<?>> handlers;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Executor outboxTaskExecutor;

    private Map<String, EventHandler<?>> handlerMap;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @PostConstruct
    public void init() {
        handlerMap = handlers.stream()
            .collect(Collectors.toMap(
                EventHandler::getEventType,
                h -> h,
                (a, b) -> {
                    throw new IllegalStateException(
                        "Handlers duplicados para el tipo de evento: " + a.getEventType()
                        + " → " + a.getClass().getSimpleName()
                        + " vs " + b.getClass().getSimpleName());
                }));
        log.info("Outbox processor initialized con {} handlers: {}",
            handlers.size(), handlerMap.keySet());
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:1000}")
    public void processPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findProcessableEvents(
            OutboxEventStatus.PENDING,
            Instant.now(),
            PageRequest.of(0, batchSize)
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Procesando {} eventos pendientes", pendingEvents.size());

        // PARALELO: procesar eventos independientes concurrentemente
        List<CompletableFuture<Void>> futures = pendingEvents.stream()
            .map(event -> CompletableFuture.runAsync(() -> {
                try {
                    transactionTemplate.executeWithoutResult(
                        status -> processEvent(event));
                } catch (RuntimeException e) {
                    persistFailureState(event.getId(), e);
                }
            }, outboxTaskExecutor))
            .toList();

        // Esperar a que todos completen antes del siguiente ciclo
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.debug("Lote de {} eventos procesado", pendingEvents.size());
    }

    private void processEvent(OutboxEvent event) {
        event.setStatus(OutboxEventStatus.PROCESSING);
        outboxRepository.save(event);

        EventHandler<?> handler = handlerMap.get(event.getEventType());
        if (handler == null) {
            throw new IllegalArgumentException(
                "No hay handler registrado para el tipo de evento: " + event.getEventType());
        }

        Object payload;
        try {
            payload = objectMapper.readValue(event.getPayload(), handler.getPayloadClass());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Error deserializando payload para eventType=" + event.getEventType(), e);
        }
        handleTypedEvent(handler, payload);

        event.setStatus(OutboxEventStatus.COMPLETED);
        event.setProcessedAt(Instant.now());
        outboxRepository.save(event);

        log.info("Evento procesado exitosamente: id={}, type={}, aggregate={}/{}",
            event.getId(), event.getEventType(),
            event.getAggregateType(), event.getAggregateId());
    }

    private void persistFailureState(UUID eventId, Exception cause) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                OutboxEvent event = outboxRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException(
                        "Evento outbox no encontrado al persistir fallo: " + eventId));
                handleFailure(event, cause);
            });
        } catch (RuntimeException persistenceError) {
            log.error("No se pudo persistir estado de fallo del evento outbox: id={}, cause={}",
                eventId, deriveErrorDescription(persistenceError), persistenceError);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleTypedEvent(EventHandler<?> handler, Object payload) {
        ((EventHandler<Object>) handler).handle(payload);
    }

    private void handleFailure(OutboxEvent event, Exception e) {
        String errorDescription = deriveErrorDescription(e);
        event.setRetryCount(event.getRetryCount() + 1);
        event.setErrorMessage(errorDescription);

        if (event.getRetryCount() >= event.getMaxRetries()) {
            event.setStatus(OutboxEventStatus.FAILED);
            moveToDeadLetter(event, errorDescription);
            log.error("Evento movido a DLQ: id={}, type={}, attempts={}, error={}",
                event.getId(), event.getEventType(),
                event.getRetryCount(), errorDescription);
        } else {
            event.setStatus(OutboxEventStatus.PENDING);
            event.setNextRetryAt(calculateNextRetry(event.getRetryCount()));
            log.warn("Reprogramando evento: id={}, type={}, attempt={}, nextRetry={}",
                event.getId(), event.getEventType(),
                event.getRetryCount(), event.getNextRetryAt());
        }

        outboxRepository.save(event);
    }

    private String deriveErrorDescription(Exception e) {
        String raw = e.getMessage();
        if (raw == null) {
            raw = e.getClass().getSimpleName();
        }
        if (raw.length() > MAX_ERROR_LENGTH) {
            raw = raw.substring(0, MAX_ERROR_LENGTH) + "...";
        }
        return raw;
    }

    /**
     * Backoff exponencial con jitter y tope: 1-2s, 2-4s, 4-8s, ... hasta 60s máximo.
     */
    private Instant calculateNextRetry(int retryCount) {
        long baseMs = (long) Math.pow(2, retryCount) * 1000;
        long capped = Math.min(baseMs, MAX_BACKOFF_MS);
        long jitter = ThreadLocalRandom.current().nextLong(0, capped / 2 + 1);
        return Instant.now().plusMillis(capped + jitter);
    }

    private void moveToDeadLetter(OutboxEvent event, String errorDescription) {
        DeadLetterEvent deadLetter = DeadLetterEvent.builder()
            .originalEventId(event.getId())
            .eventType(event.getEventType())
            .payload(event.getPayload())
            .errorMessage(errorDescription)
            .correlationId(event.getCorrelationId())
            .failedAt(Instant.now())
            .build();

        deadLetterRepository.save(deadLetter);
    }
}
