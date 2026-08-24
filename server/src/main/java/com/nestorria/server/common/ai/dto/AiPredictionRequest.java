package com.nestorria.server.common.ai.dto;

import java.util.Map;

public record AiPredictionRequest(
    String propertyId,
    String bookingId,
    Map<String, Object> features
) {
    public AiPredictionRequest {
        if (features == null) {
            throw new IllegalArgumentException("features must not be null");
        }
    }

    // Factory para predicción de precio
    public static AiPredictionRequest forPrice(String propertyId, Map<String, Object> features) {
        return new AiPredictionRequest(propertyId, null, features);
    }

    // Factory para predicción de cancelación
    public static AiPredictionRequest forCancellation(String bookingId, Map<String, Object> features) {
        return new AiPredictionRequest(null, bookingId, features);
    }

    /** Validate before calling predictPrice: propertyId required, bookingId must be null. */
    public void validateForPrice() {
        if (propertyId == null || propertyId.isBlank()) {
            throw new IllegalArgumentException("propertyId is required for price prediction");
        }
        if (bookingId != null) {
            throw new IllegalArgumentException("bookingId must be null for price prediction");
        }
    }

    /** Validate before calling predictCancellation: bookingId required, propertyId must be null. */
    public void validateForCancellation() {
        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("bookingId is required for cancellation prediction");
        }
        if (propertyId != null) {
            throw new IllegalArgumentException("propertyId must be null for cancellation prediction");
        }
    }
}
