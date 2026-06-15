package com.nestorria.server.modules.agency.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AgencyRegistrationRequest(
    @NotBlank String name,
    @NotBlank String address,
    @NotBlank String contact,
    @NotBlank @Email String email,
    @NotBlank String city
) {}
