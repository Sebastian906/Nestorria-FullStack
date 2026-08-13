package com.nestorria.server.modules.properties.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record NearbySearchRequest(
    @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
    @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
    Double radiusKm,
    String city,
    String propertyType,
    Integer minPrice,
    Integer maxPrice,
    Long categoryId
) {
    public NearbySearchRequest {
        if (radiusKm == null || radiusKm <= 0) radiusKm = 10.0;
        if (radiusKm > 100) radiusKm = 100.0;
    }
}
