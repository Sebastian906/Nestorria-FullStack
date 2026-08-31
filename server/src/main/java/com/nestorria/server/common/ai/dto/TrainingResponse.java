package com.nestorria.server.common.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrainingResponse(
    @JsonProperty("jobId") String jobId,
    String status
) { }

