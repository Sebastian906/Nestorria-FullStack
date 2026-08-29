package com.nestorria.server.common.ai.dto;

import java.util.List;

public record ToolPropertySearchResponse(
    List<ToolPropertySummary> properties,
    int count
) {
    public record ToolPropertySummary(
        String id,
        String title,
        String city,
        String propertyType,
        Integer salePrice,
        Integer rentPrice,
        int area,
        String address
    ) {}
}
