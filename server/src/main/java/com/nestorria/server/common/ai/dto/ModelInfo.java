package com.nestorria.server.common.ai.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ModelInfo(
    String name,
    String version,
    String status,
    Map<String, Object> metrics,
    @JsonProperty("lastTrained") String lastTrained
) { }

