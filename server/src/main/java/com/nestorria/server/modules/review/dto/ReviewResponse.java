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
    String originalLang,
    String displayComment,
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
            r.getOriginalLang(),
            r.getComment(),
            r.isVerified(),
            r.getCreatedAt());
    }

    public static ReviewResponse of(Review r, String display) {
        return new ReviewResponse(
            r.getId(),
            r.getProperty().getId(),
            r.getUser().getId(),
            r.getUser().getUsername(),
            r.getUser().getImage(),
            r.getRating(),
            r.getComment(),
            r.getOriginalLang(),
            display,
            r.isVerified(),
            r.getCreatedAt());
    }
}
