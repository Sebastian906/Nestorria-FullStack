package com.nestorria.server.common.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminAiStatusResponse(
    String status,
    @JsonProperty("modelsLoaded") List<String> modelsLoaded,
    @JsonProperty("ragEnabled") Boolean ragEnabled,
    @JsonProperty("llmEnabled") Boolean llmEnabled
) {
    public static AdminAiStatusResponse degraded() {
        return new AdminAiStatusResponse("degraded", List.of(), false, false);
    }
}
