package com.nestorria.server.common.ai.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

public record VersionInfoResponse(
    String version,
    Map<String, Object> metrics,
    String date,
    List<String> features
) { }
