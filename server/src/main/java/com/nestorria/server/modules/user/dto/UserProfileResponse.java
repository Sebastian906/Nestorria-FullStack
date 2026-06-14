package com.nestorria.server.modules.user.dto;

import java.util.List;

import com.nestorria.server.modules.user.UserRole;

public record UserProfileResponse(UserRole role, List<String> recentSearchedCities) {}
