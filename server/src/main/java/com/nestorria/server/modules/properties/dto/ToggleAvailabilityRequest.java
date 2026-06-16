package com.nestorria.server.modules.properties.dto;

import jakarta.validation.constraints.NotBlank;

public record ToggleAvailabilityRequest(@NotBlank String propertyId) {}
