package com.nestorria.server.modules.booking.dto;

import java.util.List;

public record MultiAvailabilityResponse(
    boolean allAvailable,
    List<PropertyAvailabilityResult> results
) {}
