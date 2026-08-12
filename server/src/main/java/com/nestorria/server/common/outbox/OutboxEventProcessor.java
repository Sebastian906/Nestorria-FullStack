package com.nestorria.server.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    private final OutboxEventRepository outboxRepository;
    private final DeadLetterEventRepository deadLetterRepository;
    private final List<EventHandler> handlers;
    private final ObjectMapper objectMapper;

    private Map<String, EventHandler> handlerMap;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @PostConstruct
    public void init() {
        handlerMap = handlers.stream()
            .collect(Collectors.toMap(EventHandler::getEventType, h -> h));
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

    private void processEvent(OutboxEvent event) {
        // Marcar como PROCESSING
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

            // Marcar como COMPLETED
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
        event.setRetryCount(event.getRetryCount() + 1);
        event.setErrorMessage(e.getMessage());

        if (event.getRetryCount() >= event.getMaxRetries()) {
            // Agotados los reintentos → mover a DLQ
            event.setStatus(OutboxEventStatus.FAILED);
            moveToDeadLetter(event, e);
            log.error("Evento movido a DLQ: id={}, type={}, attempts={}, error={}",
                event.getId(), event.getEventType(),
                event.getRetryCount(), e.getMessage());
        } else {
            // Reprogramar con backoff exponencial
            event.setStatus(OutboxEventStatus.PENDING);
            event.setNextRetryAt(calculateNextRetry(event.getRetryCount()));
            log.warn("Reprogramando evento: id={}, type={}, attempt={}, nextRetry={}",
                event.getId(), event.getEventType(),
                event.getRetryCount(), event.getNextRetryAt());
        }

        outboxRepository.save(event);
    }

    /**
     * Backoff exponencial: 1s, 2s, 4s, 8s, 16s
     */
    private Instant calculateNextRetry(int retryCount) {
        long delayMs = (long) Math.pow(2, retryCount) * 1000;
        return Instant.now().plusMillis(delayMs);
    }

    private void moveToDeadLetter(OutboxEvent event, Exception e) {
        DeadLetterEvent deadLetter = DeadLetterEvent.builder()
            .originalEventId(event.getId())
            .eventType(event.getEventType())
            .payload(event.getPayload())
            .errorMessage(e.getMessage())
            .correlationId(event.getCorrelationId())
            .failedAt(Instant.now())
            .build();

        deadLetterRepository.save(deadLetter);
    }
}
