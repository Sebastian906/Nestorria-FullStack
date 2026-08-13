package com.nestorria.server.modules.properties.dto;

import java.util.List;

import com.nestorria.server.modules.properties.CategoryTree;

public record CategoryNode(
    Long id,
    String name,
    String slug,
    String description,
    int level,
    List<CategoryNode> children
) {
    public static CategoryNode fromEntity(CategoryTree entity) {
        return new CategoryNode(
            entity.getId(),
            entity.getName(),
            entity.getSlug(),
            entity.getDescription(),
            entity.getLevel(),
            List.of() // children populated separately in buildTree
        );
    }
}
