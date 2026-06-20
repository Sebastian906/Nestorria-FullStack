package com.nestorria.server.modules.booking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CheckAvailabilityRequest(
    @NotBlank String propertyId,
    @NotNull LocalDate checkInDate,
    @NotNull LocalDate checkOutDate
) {}
