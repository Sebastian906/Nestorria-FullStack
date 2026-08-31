package com.nestorria.server.common.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

public record TrainingResponse(
    @JsonAlias("job_id") @JsonProperty("jobId") String jobId,
    String status
) { }

