package com.nestorria.server.common.ai.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

public record CompareVersionsResponse(
    String model,
    @JsonAlias("version_1") @JsonProperty("version1") String version1,
    @JsonAlias("version_2") @JsonProperty("version2") String version2,
    @JsonAlias("date_1") @JsonProperty("date1") String date1,
    @JsonAlias("date_2") @JsonProperty("date2") String date2,
    @JsonAlias("metrics_comparison") @JsonProperty("metricsComparison") Map<String, Object> metricsComparison
) { }
