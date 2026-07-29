package com.nestorria.server.modules.review.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateReviewRequest(
    @Min(1) @Max(5) Integer rating,
    @Size(max = 2000) String comment
) {
    @AssertTrue(message = "Al menos uno de rating o comment debe ser proporcionado")
    public boolean isValid() {
        return rating != null || (comment != null && !comment.isBlank());
    }
}
