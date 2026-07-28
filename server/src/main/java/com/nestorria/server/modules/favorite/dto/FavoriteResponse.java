package com.nestorria.server.modules.favorite.dto;

import java.time.Instant;

import com.nestorria.server.modules.favorite.Favorite;

public record FavoriteResponse(
    String id,
    String propertyId,
    String propertyTitle,
    String propertyCity,
    String propertyImage,
    Integer propertyPrice,
    Instant favoritedAt
) {
    public static FavoriteResponse fromEntity(Favorite f) {
        return new FavoriteResponse(
            f.getId(),
            f.getProperty().getId(),
            f.getProperty().getTitle(),
            f.getProperty().getCity(),
            f.getProperty().getImages().isEmpty() ? null : f.getProperty().getImages().get(0),
            f.getProperty().getPrice().getRent() != null
                ? f.getProperty().getPrice().getRent()
                : f.getProperty().getPrice().getSale(),
            f.getCreatedAt()
        );
    }
}
