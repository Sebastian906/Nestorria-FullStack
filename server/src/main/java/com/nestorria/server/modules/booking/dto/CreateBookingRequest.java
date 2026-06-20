package com.nestorria.server.modules.booking.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
    @NotBlank String propertyId,
    @NotNull LocalDate checkInDate,
    @NotNull LocalDate checkOutDate,
    @Min(1) int guests
) {}
