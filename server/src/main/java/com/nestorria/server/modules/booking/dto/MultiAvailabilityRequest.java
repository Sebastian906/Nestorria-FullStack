package com.nestorria.server.modules.booking.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record MultiAvailabilityRequest(
    @NotEmpty List<String> propertyIds,
    @NotNull LocalDate checkInDate,
    @NotNull LocalDate checkOutDate,
    @Min(1) int guests
) {}
