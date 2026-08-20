package com.nestorria.server.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerInstanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void health_returnsUpAndStableInstanceId() throws Exception {
        JsonNode first = getHealth();
        JsonNode second = getHealth();

        assertThat(first.get("status").asText()).isEqualTo("UP");
        assertThat(first.get("timestamp").isNull()).isFalse();
        // La identidad debe ser estable entre requests de la misma instancia
        assertThat(first.get("instanceId").asText())
            .isNotBlank()
            .isEqualTo(second.get("instanceId").asText());
    }

    private JsonNode getHealth() throws Exception {
        String body = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/health/"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body);
    }
}
