package com.nestorria.server.modules.user.dto;

import jakarta.validation.constraints.NotBlank;

public record AddRecentSearchRequest(@NotBlank String city) {}
