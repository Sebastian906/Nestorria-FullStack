package com.nestorria.server.modules.review.dto;

import java.time.Instant;

import com.nestorria.server.modules.review.Review;

public record ReviewResponse(
    String id,
    String propertyId,
    String userId,
    String userName,
    String userImage,
    int rating,
    String comment,
    boolean isVerified,
    Instant createdAt
) {
    public static ReviewResponse fromEntity(Review r) {
        return new ReviewResponse(
            r.getId(),
            r.getProperty().getId(),
            r.getUser().getId(),
            r.getUser().getUsername(),
            r.getUser().getImage(),
            r.getRating(),
            r.getComment(),
            r.isVerified(),
            r.getCreatedAt()
        );
    }
}
