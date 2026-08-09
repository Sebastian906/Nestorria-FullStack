package com.nestorria.server.modules.properties.dto;

import java.util.Map;

public record PropertyStatsResponse(
    long totalProperties,
    Map<String, Long> byType,
    Map<String, Long> byCity
) {}
