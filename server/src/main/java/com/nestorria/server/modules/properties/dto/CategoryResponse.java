package com.nestorria.server.modules.properties.dto;

import java.util.List;

import com.nestorria.server.modules.properties.CategoryTree;

public record CategoryResponse(
    Long id,
    String name,
    String slug,
    String description,
    int level,
    Long parentId,
    List<CategoryNode> children
) {
    public static CategoryResponse fromEntity(CategoryTree entity, List<CategoryNode> children) {
        return new CategoryResponse(
            entity.getId(),
            entity.getName(),
            entity.getSlug(),
            entity.getDescription(),
            entity.getLevel(),
            entity.getParent() != null ? entity.getParent().getId() : null,
            children
        );
    }
}
