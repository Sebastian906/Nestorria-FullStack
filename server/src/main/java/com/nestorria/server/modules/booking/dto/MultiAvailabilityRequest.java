package com.nestorria.server.modules.booking.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record MultiAvailabilityRequest(
    @NotEmpty List<@NotBlank String> propertyIds,
    @NotNull LocalDate checkInDate,
    @NotNull LocalDate checkOutDate
) {}
