package com.nestorria.server.common.ai.dto;

import java.util.Map;

public record ToolPropertyCountResponse(
    long count,
    Map<String, String> filters
) {}
