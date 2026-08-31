package com.nestorria.server.common.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

public record PromoteRollbackResponse(
    String name,
    @JsonAlias("previous_version") @JsonProperty("previousVersion") String previousVersion,
    @JsonAlias("new_version") @JsonProperty("newVersion") String newVersion
) { }
