package com.nestorria.server.common.ai.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

public record ModelInfo(
    String name,
    String version,
    String status,
    Map<String, Object> metrics,
    @JsonAlias("last_trained") @JsonProperty("lastTrained") String lastTrained
) { }

