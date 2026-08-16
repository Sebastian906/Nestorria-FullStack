package com.nestorria.server.modules.booking.dto;

public record PropertyAvailabilityResult(
    String propertyId,
    boolean available,
    String reason
) {
    public static PropertyAvailabilityResult available(String propertyId) {
        return new PropertyAvailabilityResult(propertyId, true, null);
    }

    public static PropertyAvailabilityResult unavailable(String propertyId, String reason) {
        return new PropertyAvailabilityResult(propertyId, false, reason);
    }
}
