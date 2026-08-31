package com.nestorria.server.common.ai.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminChatMetricsResponse(
    @JsonProperty("totalMessages") Integer totalMessages,
    @JsonProperty("messagesByUser") Map<String, Integer> messagesByUser,
    @JsonProperty("averageResponseTime") Double averageResponseTime,
    @JsonProperty("errorRate") Double errorRate
) {
    public static AdminChatMetricsResponse empty() {
        return new AdminChatMetricsResponse(0, Map.of(), 0.0, 0.0);
    }
}

