package com.nestorria.server.modules.review;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.modules.review.dto.CreateReviewRequest;
import com.nestorria.server.modules.review.dto.ReviewResponse;
import com.nestorria.server.modules.review.dto.UpdateReviewRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Reviews", description = "Gestión de reseñas y calificaciones de propiedades")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Crear una reseña para una propiedad (requiere autenticación)")
    @ApiResponse(responseCode = "201", description = "Reseña creada exitosamente")
    @ApiResponse(responseCode = "409", description = "El usuario ya ha reseñado esta propiedad")
    @PostMapping("/properties/{propertyId}/reviews")
    public ResponseEntity<ReviewResponse> createReview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String propertyId,
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse response = reviewService.createReview(jwt.getSubject(), propertyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Obtener las reseñas de una propiedad (público)")
    @ApiResponse(responseCode = "200", description = "Lista de reseñas obtenida exitosamente")
    @GetMapping("/properties/{propertyId}/reviews")
    public List<ReviewResponse> getPropertyReviews(@PathVariable String propertyId) {
        return reviewService.getPropertyReviews(propertyId);
    }

    @GetMapping("/reviews/me")
    public List<ReviewResponse> getMyReviews(@AuthenticationPrincipal Jwt jwt) {
        return reviewService.getUserReviews(jwt.getSubject());
    }

    @Operation(summary = "Actualizar una reseña (requiere autenticación y ser autor)")
    @ApiResponse(responseCode = "200", description = "Reseña actualizada exitosamente")
    @ApiResponse(responseCode = "403", description = "No eres el autor de esta reseña")
    @PatchMapping("/reviews/{id}")
    public ReviewResponse updateReview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @Valid @RequestBody UpdateReviewRequest request) {
        return reviewService.updateReview(id, jwt.getSubject(), request);
    }

    @Operation(summary = "Eliminar una reseña (requiere autenticación y ser autor)")
    @ApiResponse(responseCode = "204", description = "Reseña eliminada exitosamente")
    @ApiResponse(responseCode = "403", description = "No eres el autor de esta reseña")
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        reviewService.deleteReview(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
