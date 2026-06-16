package com.nestorria.server.modules.properties.dto;

import java.util.List;

import com.nestorria.server.modules.properties.PropertyType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePropertyRequest(
    @NotBlank String title,
    @NotBlank String description,
    @NotBlank String city,
    @NotBlank String country,
    @NotBlank String address,
    @Min(1) int area,
    @NotNull PropertyType propertyType,
    @Min(0) Integer priceRent,
    @Min(0) Integer priceSale,
    @Min(0) int bedrooms,
    @Min(0) int bathrooms,
    @Min(0) int garages,
    @NotNull List<String> amenities
) {}
