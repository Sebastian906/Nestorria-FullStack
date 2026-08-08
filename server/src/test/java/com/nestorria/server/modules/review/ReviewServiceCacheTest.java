package com.nestorria.server.modules.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.github.benmanes.caffeine.cache.Cache;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.properties.PropertyType;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.properties.embeddable.PropertyLocation;
import com.nestorria.server.modules.review.ReviewService.RatingAggregate;
import com.nestorria.server.modules.review.dto.CreateReviewRequest;
import com.nestorria.server.modules.review.dto.UpdateReviewRequest;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

@SpringBootTest
class ReviewServiceCacheTest {

    @Autowired private ReviewService reviewService;
    @Autowired private CacheManager cacheManager;

    @MockitoBean private ReviewRepository reviewRepository;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private PropertyRepository propertyRepository;

    private Property testProperty;
    private User testUser;
    private Review testReview;

    private record CacheStats(long hitCount, long missCount) {}

    private CacheStats snapshotStats(String cacheName) {
        CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(cacheName);
        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        return new CacheStats(nativeCache.stats().hitCount(), nativeCache.stats().missCount());
    }

    private void clearCache(String cacheName) {
        cacheManager.getCache(cacheName).clear();
    }

    @SafeVarargs
    private final List<Object[]> aggregates(Object[]... rows) {
        return List.of(rows);
    }

    @BeforeEach
    void setUp() {
        clearCache("ratingAggregates");
        clearCache("propertyListings");
        clearCache("ownerProperties");

        testUser = new User("user-1", "Test User", "test@test.com", "img.jpg");

        testProperty = new Property(
            null, "Test Property", "Desc", "Madrid", "Spain", "Addr 123", 100,
            PropertyType.APARTMENT,
            new PriceDetails(1000, 200000),
            new FacilityDetails(2, 1, 1),
            List.of(),
            new PropertyLocation(40.4, -3.7, null, null)
        );
        testProperty.setId("prop-1");

        testReview = new Review(testUser, testProperty, 4, "Great property");
        testReview.setId("review-1");
    }

    // ==================== getAverageRatings ====================

    @Test
    void getAverageRatings_CacheMiss_ThenCacheHit() {
        List<String> propertyIds = List.of("prop-1");
        when(reviewRepository.findRatingAggregatesByPropertyIds(propertyIds))
            .thenReturn(aggregates(new Object[]{"prop-1", 4.5, 10L}));

        CacheStats before = snapshotStats("ratingAggregates");
        reviewService.getAverageRatings(propertyIds);
        CacheStats afterFirst = snapshotStats("ratingAggregates");
        reviewService.getAverageRatings(propertyIds);
        CacheStats afterSecond = snapshotStats("ratingAggregates");

        assertEquals(before.missCount() + 1, afterFirst.missCount());
        assertEquals(afterFirst.missCount(), afterSecond.missCount());
        assertEquals(afterFirst.hitCount() + 1, afterSecond.hitCount());
    }

    @Test
    void getAverageRatings_EmptyList_ReturnsEmptyMap() {
        Map<String, RatingAggregate> result = reviewService.getAverageRatings(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void getAverageRatings_NullList_ReturnsEmptyMap() {
        Map<String, RatingAggregate> result = reviewService.getAverageRatings(null);
        assertTrue(result.isEmpty());
    }

    // ==================== createReview evicts ====================

    @Test
    void createReview_EvictsRatingAggregatesCache() {
        List<String> propertyIds = List.of("prop-1");
        when(reviewRepository.findRatingAggregatesByPropertyIds(propertyIds))
            .thenReturn(aggregates(new Object[]{"prop-1", 4.0, 1L}));

        // Populate cache
        reviewService.getAverageRatings(propertyIds);
        CacheStats afterPopulate = snapshotStats("ratingAggregates");

        // Create a new review — should evict ratingAggregates cache
        User user2 = new User("user-2", "User2", "user2@test.com", "img2.jpg");
        when(userRepository.findById("user-2")).thenReturn(Optional.of(user2));
        when(propertyRepository.findById("prop-1")).thenReturn(Optional.of(testProperty));
        when(reviewRepository.existsByUserIdAndPropertyId("user-2", "prop-1")).thenReturn(false);
        Review newReview = new Review(user2, testProperty, 5, "Excellent");
        newReview.setId("review-2");
        when(reviewRepository.save(any(Review.class))).thenReturn(newReview);

        reviewService.createReview("user-2", "prop-1", new CreateReviewRequest(5, "Excellent"));

        // After eviction: next call misses
        reviewService.getAverageRatings(propertyIds);
        CacheStats afterEvict = snapshotStats("ratingAggregates");

        assertEquals(afterPopulate.missCount() + 1, afterEvict.missCount());
    }

    // ==================== updateReview evicts ====================

    @Test
    void updateReview_EvictsRatingAggregatesCache() {
        List<String> propertyIds = List.of("prop-1");
        when(reviewRepository.findRatingAggregatesByPropertyIds(propertyIds))
            .thenReturn(aggregates(new Object[]{"prop-1", 4.0, 1L}));

        reviewService.getAverageRatings(propertyIds);
        CacheStats afterPopulate = snapshotStats("ratingAggregates");

        when(reviewRepository.findById("review-1")).thenReturn(Optional.of(testReview));
        when(reviewRepository.save(any(Review.class))).thenReturn(testReview);

        reviewService.updateReview("review-1", "user-1", new UpdateReviewRequest(3, "Updated"));

        reviewService.getAverageRatings(propertyIds);
        CacheStats afterEvict = snapshotStats("ratingAggregates");

        assertEquals(afterPopulate.missCount() + 1, afterEvict.missCount());
    }

    // ==================== deleteReview evicts ====================

    @Test
    void deleteReview_EvictsRatingAggregatesCache() {
        List<String> propertyIds = List.of("prop-1");
        when(reviewRepository.findRatingAggregatesByPropertyIds(propertyIds))
            .thenReturn(aggregates(new Object[]{"prop-1", 4.0, 1L}));

        reviewService.getAverageRatings(propertyIds);
        CacheStats afterPopulate = snapshotStats("ratingAggregates");

        when(reviewRepository.findById("review-1")).thenReturn(Optional.of(testReview));

        reviewService.deleteReview("review-1", "user-1");

        reviewService.getAverageRatings(propertyIds);
        CacheStats afterEvict = snapshotStats("ratingAggregates");

        assertEquals(afterPopulate.missCount() + 1, afterEvict.missCount());
    }
}
