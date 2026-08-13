package com.nestorria.server.modules.properties.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateCategoryRequest(
    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Slug is required")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must be lowercase alphanumeric with hyphens")
    String slug,

    String description,

    Long parentId
) {}
