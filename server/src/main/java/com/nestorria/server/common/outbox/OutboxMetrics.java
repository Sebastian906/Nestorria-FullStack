package com.nestorria.server.common.outbox;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OutboxMetrics {

    private final OutboxEventRepository outboxRepository;
    private final DeadLetterEventRepository deadLetterRepository;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    void bind() {
        Gauge.builder("outbox.pending",
                outboxRepository, r -> r.countByStatus(OutboxEventStatus.PENDING))
            .register(meterRegistry);
        Gauge.builder("outbox.dead_letter",
                deadLetterRepository, r -> r.count())
            .register(meterRegistry);
    }
}
