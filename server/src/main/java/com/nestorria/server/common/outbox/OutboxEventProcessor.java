package com.nestorria.server.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
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

        for (OutboxEvent event : pendingEvents) {
            processEvent(event);
        }
    }

    @SuppressWarnings("unchecked")
    private void processEvent(OutboxEvent event) {
        event.setStatus(OutboxEventStatus.PROCESSING);
        outboxRepository.save(event);

        try {
            EventHandler handler = handlerMap.get(event.getEventType());
            if (handler == null) {
                throw new IllegalArgumentException(
                    "No hay handler registrado para el tipo de evento: " + event.getEventType());
            }

            Object payload = objectMapper.readValue(
                event.getPayload(), handler.getPayloadClass());
            handler.handle(payload);

            event.setStatus(OutboxEventStatus.COMPLETED);
            event.setProcessedAt(Instant.now());
            outboxRepository.save(event);

            log.info("Evento procesado exitosamente: id={}, type={}, aggregate={}/{}",
                event.getId(), event.getEventType(),
                event.getAggregateType(), event.getAggregateId());

        } catch (Exception e) {
            handleFailure(event, e);
        }
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
