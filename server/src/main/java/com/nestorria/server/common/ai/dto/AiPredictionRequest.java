package com.nestorria.server.common.ai.dto;

import java.util.Map;

public record AiPredictionRequest(
    String propertyId,
    String bookingId,
    Map<String, Object> features
) {
    // Factory para predicción de precio
    public static AiPredictionRequest forPrice(String propertyId, Map<String, Object> features) {
        return new AiPredictionRequest(propertyId, null, features);
    }

    // Factory para predicción de cancelación
    public static AiPredictionRequest forCancellation(String bookingId, Map<String, Object> features) {
        return new AiPredictionRequest(null, bookingId, features);
    }
}
