package com.nestorria.server.modules.review;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.exception.ConflictException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.review.dto.CreateReviewRequest;
import com.nestorria.server.modules.review.dto.ReviewResponse;
import com.nestorria.server.modules.review.dto.UpdateReviewRequest;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            UserRepository userRepository,
            PropertyRepository propertyRepository) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
    }

    @CacheEvict(cacheNames = {"ratingAggregates", "propertyListings", "ownerProperties"}, allEntries = true)
    @Transactional
    public ReviewResponse createReview(String userId, String propertyId, CreateReviewRequest request) {
        if (reviewRepository.existsByUserIdAndPropertyId(userId, propertyId)) {
            throw new ConflictException("Ya has publicado una reseña para esta propiedad");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userId));

        Property property = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Propiedad no encontrada: " + propertyId));

        Review review = new Review(user, property, request.rating(), request.comment());
        return ReviewResponse.fromEntity(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getPropertyReviews(String propertyId) {
        return reviewRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId)
            .stream()
            .map(ReviewResponse::fromEntity)
            .toList();
    }

    @CacheEvict(cacheNames = {"ratingAggregates", "propertyListings", "ownerProperties"}, allEntries = true)
    @Transactional
    public ReviewResponse updateReview(String reviewId, String userId, UpdateReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new ResourceNotFoundException("Reseña no encontrada: " + reviewId));

        if (!review.getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes permiso para modificar esta reseña"
            );
        }

        if (request.rating() != null) {
            review.setRating(request.rating());
        }
        if (request.comment() != null) {
            review.setComment(request.comment());
        }

        return ReviewResponse.fromEntity(reviewRepository.save(review));
    }

    @CacheEvict(cacheNames = {"ratingAggregates", "propertyListings", "ownerProperties"}, allEntries = true)
    @Transactional
    public void deleteReview(String reviewId, String userId) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new ResourceNotFoundException("Reseña no encontrada: " + reviewId));

        if (!review.getUser().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes permiso para eliminar esta reseña"
            );
        }

        reviewRepository.delete(review);
    }

    @Cacheable(cacheNames = "ratingAggregates")
    @Transactional(readOnly = true)
    public Map<String, RatingAggregate> getAverageRatings(List<String> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()) {
            return Map.of();
        }

        List<Object[]> aggregates = reviewRepository.findRatingAggregatesByPropertyIds(propertyIds);

        Map<String, RatingAggregate> result = new HashMap<>();
        for (Object[] row : aggregates) {
            String propertyId = (String) row[0];
            Double avgRating = (Double) row[1];
            Long count = (Long) row[2];
            result.put(propertyId, new RatingAggregate(avgRating, count.intValue()));
        }
        return result;
    }

    public record RatingAggregate(Double averageRating, int reviewCount) {}
}
