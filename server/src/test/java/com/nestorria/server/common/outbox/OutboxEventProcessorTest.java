package com.nestorria.server.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
    private DeadLetterEventRepository deadLetterRepository;

    @Autowired
    private OutboxEventService outboxEventService;

    @AfterEach
    void cleanup() {
        deadLetterRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
    }

    @Test
    void processPendingEvents_handlesEventSuccessfully() {
        outboxEventService.publish(
            new NotificationEvent("user-1", NotificationType.BOOKING_CONFIRMED,
                "Test", "Test message", "booking", "b-1"),
            "Booking", "b-1");

        OutboxEvent saved = outboxRepository.findAll().get(0);
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

        processor.processPendingEvents();

        OutboxEvent after = outboxRepository.findById(saved.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OutboxEventStatus.COMPLETED);
        assertThat(after.getProcessedAt()).isNotNull();
    }

    @Test
    void processPendingEvents_movesFailedEventToDLQ_afterMaxRetries() {
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
        UUID eventId = event.getId();

        // Primer intento fallido → retryCount=1, nextRetryAt en el futuro
        processor.processPendingEvents();
        OutboxEvent after1 = outboxRepository.findById(eventId).orElseThrow();
        assertThat(after1.getRetryCount()).isEqualTo(1);
        assertThat(after1.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

        // Avanzar el reloj del evento para que sea procesable
        after1.setNextRetryAt(null);
        outboxRepository.save(after1);
        processor.processPendingEvents();
        OutboxEvent after2 = outboxRepository.findById(eventId).orElseThrow();
        assertThat(after2.getRetryCount()).isEqualTo(2);

        after2.setNextRetryAt(null);
        outboxRepository.save(after2);
        processor.processPendingEvents();

        OutboxEvent failed = outboxRepository.findById(eventId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(3);

        DeadLetterEvent dlq = deadLetterRepository.findAll().stream()
            .filter(d -> d.getOriginalEventId().equals(eventId))
            .findFirst().orElseThrow();
        assertThat(dlq.getEventType()).isEqualTo("InvalidEventType");
        assertThat(dlq.getErrorMessage()).isNotBlank();
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

        processor.processPendingEvents();

        OutboxEvent afterFirstFailure = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(afterFirstFailure.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(afterFirstFailure.getRetryCount()).isEqualTo(1);
        assertThat(afterFirstFailure.getNextRetryAt()).isNotNull();
        assertThat(afterFirstFailure.getErrorMessage()).isNotBlank();
    }

    @Test
    void processPendingEvents_errorDescription_neverNull() {
        OutboxEvent event = OutboxEvent.builder()
            .eventType("InvalidEventType")
            .payload("{}")
            .aggregateType("Test")
            .aggregateId("test-3")
            .status(OutboxEventStatus.PENDING)
            .maxRetries(1)
            .retryCount(0)
            .build();
        outboxRepository.save(event);

        processor.processPendingEvents();

        OutboxEvent after = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(after.getErrorMessage()).isNotNull().isNotEmpty();
    }

    @Test
    void claimEvent_onlyFirstClaimWins() {
        outboxEventService.publish(
            new NotificationEvent("user-1", NotificationType.BOOKING_CONFIRMED,
                "Test", "Test message", "booking", "b-1"),
            "Booking", "b-1");
        UUID eventId = outboxRepository.findAll().get(0).getId();

        assertThat(outboxRepository.claimEvent(eventId)).isEqualTo(1); // yo gano
        assertThat(outboxRepository.claimEvent(eventId)).isEqualTo(0); // segunda instancia pierde
    }
}
