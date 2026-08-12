package com.nestorria.server.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.modules.notification.NotificationType;

@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.flyway.enabled=false")
class OutboxEventProcessorTest {

    @Autowired
    private OutboxEventProcessor processor;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private OutboxEventService outboxEventService;

    @Test
    void processPendingEvents_handlesEventSuccessfully() {
        // Publicar evento
        outboxEventService.publish(
            new NotificationEvent("user-1", NotificationType.BOOKING_CONFIRMED,
                "Test", "Test message", "booking", "b-1"),
            "Booking", "b-1");

        // Verificar que está pendiente
        assertThat(outboxRepository.countByStatus(OutboxEventStatus.PENDING)).isEqualTo(1);

        // Procesar
        processor.processPendingEvents();

        // Verificar que se completó
        assertThat(outboxRepository.countByStatus(OutboxEventStatus.PENDING)).isZero();
        assertThat(outboxRepository.countByStatus(OutboxEventStatus.COMPLETED)).isEqualTo(1);
    }

    @Test
    void processPendingEvents_movesFailedEventToDLQ_afterMaxRetries() {
        // Crear evento con tipo inválido (no hay handler)
        OutboxEvent event = OutboxEvent.builder()
            .eventType("InvalidEventType")
            .payload("{}")
            .aggregateType("Test")
            .aggregateId("test-1")
            .status(OutboxEventStatus.PENDING)
            .maxRetries(3)
            .retryCount(0)
            .build();
        outboxRepository.save(event);

        // Procesar 3 veces (cada intento incrementa retryCount)
        processor.processPendingEvents(); // retryCount=1
        processor.processPendingEvents(); // retryCount=2
        processor.processPendingEvents(); // retryCount=3 → FAILED + DLQ

        OutboxEvent failed = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(3);
    }

    @Test
    void processPendingEvents_retriesWithExponentialBackoff() {
        OutboxEvent event = OutboxEvent.builder()
            .eventType("InvalidEventType")
            .payload("{}")
            .aggregateType("Test")
            .aggregateId("test-2")
            .status(OutboxEventStatus.PENDING)
            .maxRetries(5)
            .retryCount(0)
            .build();
        outboxRepository.save(event);

        // Primer intento fallido
        processor.processPendingEvents();

        OutboxEvent afterFirstFailure = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(afterFirstFailure.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(afterFirstFailure.getRetryCount()).isEqualTo(1);
        assertThat(afterFirstFailure.getNextRetryAt()).isNotNull();
        // Backoff: 2^1 * 1000ms = 2 segundos
    }
}
