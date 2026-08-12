package com.nestorria.server.common.outbox;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Publica un evento en la tabla outbox.
     * DEBE ejecutarse dentro de la misma transacción que la operación de negocio.
     * Esto garantiza atomicidad: si la transacción hace rollback, el evento no se persiste.
     */
    @Transactional
    public <T> void publish(T event, String aggregateType, String aggregateId) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventType(event.getClass().getSimpleName())
                .payload(objectMapper.writeValueAsString(event))
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .correlationId(UUID.randomUUID().toString())
                .build();

            repository.save(outboxEvent);

            log.debug("Evento publicado en outbox: type={}, aggregate={}",
                event.getClass().getSimpleName(), aggregateId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                "Error serializando evento: " + event.getClass().getSimpleName(), e);
        }
    }
}
