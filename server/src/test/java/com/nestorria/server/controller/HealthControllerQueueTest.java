package com.nestorria.server.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nestorria.server.common.outbox.DeadLetterEvent;
import com.nestorria.server.common.outbox.DeadLetterEventRepository;
import com.nestorria.server.common.outbox.OutboxEvent;
import com.nestorria.server.common.outbox.OutboxEventRepository;
import com.nestorria.server.common.outbox.OutboxEventStatus;

import java.time.Instant;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.flyway.enabled=false")
class HealthControllerQueueTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private DeadLetterEventRepository deadLetterRepository;

    @AfterEach
    void cleanup() {
        deadLetterRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
    }

    @Test
    void queueHealth_returnsExactCounts() throws Exception {
        // Seed: 2 PENDING, 1 PROCESSING, 1 COMPLETED, 1 FAILED, 1 DLQ
        saveEvent(OutboxEventStatus.PENDING);
        saveEvent(OutboxEventStatus.PENDING);
        saveEvent(OutboxEventStatus.PROCESSING);
        saveEvent(OutboxEventStatus.COMPLETED);
        OutboxEvent failed = saveEvent(OutboxEventStatus.FAILED);

        DeadLetterEvent dlq = DeadLetterEvent.builder()
            .originalEventId(failed.getId())
            .eventType("TestEvent")
            .payload("{}")
            .errorMessage("test error")
            .failedAt(Instant.now())
            .build();
        deadLetterRepository.save(dlq);

        String response = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/health/queues"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(response);

        assertThat(json.get("pending").asLong()).isEqualTo(2);
        assertThat(json.get("processing").asLong()).isEqualTo(1);
        assertThat(json.get("completed").asLong()).isEqualTo(1);
        assertThat(json.get("failed").asLong()).isEqualTo(1);
        assertThat(json.get("deadLetter").asLong()).isEqualTo(1);
        assertThat(json.get("totalActive").asLong()).isEqualTo(3); // 2 pending + 1 processing
    }

    private OutboxEvent saveEvent(OutboxEventStatus status) {
        OutboxEvent event = OutboxEvent.builder()
            .eventType("TestEvent")
            .payload("{}")
            .aggregateType("Test")
            .aggregateId("agg-1")
            .status(status)
            .build();
        return outboxRepository.save(event);
    }
}
