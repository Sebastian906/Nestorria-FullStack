package com.nestorria.server.common.ai.dto;

public record ToolReviewAverageResponse(
    double averageRating,
    int reviewCount,
    String propertyId
) {}
