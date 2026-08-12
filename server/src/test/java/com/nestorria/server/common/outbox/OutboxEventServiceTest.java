package com.nestorria.server.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
class OutboxEventServiceTest {

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Test
    void publish_createsPendingEvent() {
        NotificationEvent event = new NotificationEvent(
            "user-123",
            NotificationType.BOOKING_CONFIRMED,
            "Reserva confirmada",
            "Tu reserva ha sido confirmada",
            "booking",
            "booking-456"
        );

        outboxEventService.publish(event, "Booking", "booking-456");

        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);

        OutboxEvent saved = events.get(0);
        assertThat(saved.getEventType()).isEqualTo("NotificationEvent");
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(saved.getAggregateType()).isEqualTo("Booking");
        assertThat(saved.getAggregateId()).isEqualTo("booking-456");
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getMaxRetries()).isEqualTo(5);
        assertThat(saved.getCorrelationId()).isNotNull();
        assertThat(saved.getPayload()).contains("user-123");
    }
}
