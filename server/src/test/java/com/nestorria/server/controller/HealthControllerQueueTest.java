package com.nestorria.server.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.flyway.enabled=false")
class HealthControllerQueueTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void queueHealth_returnsQueueStats() throws Exception {
        String response = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/health/queues"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(response);

        assertThat(json.has("pending")).isTrue();
        assertThat(json.has("processing")).isTrue();
        assertThat(json.has("completed")).isTrue();
        assertThat(json.has("failed")).isTrue();
        assertThat(json.has("deadLetter")).isTrue();
        assertThat(json.has("totalActive")).isTrue();
    }
}
