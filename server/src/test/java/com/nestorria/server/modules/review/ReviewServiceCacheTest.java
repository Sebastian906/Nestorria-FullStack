package com.nestorria.server.modules.review;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.properties.PropertyType;
import com.nestorria.server.modules.review.ReviewService.RatingAggregate;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceCacheTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PropertyRepository propertyRepository;

    @InjectMocks
    private ReviewService reviewService;

    private User testUser;
    private Property testProperty;

    @BeforeEach
    void setUp() {
        testUser = new User("user-1", "Test", "test@test.com", "img.jpg");

        testProperty = new Property(
            null, "Test", "Desc", "Madrid", "Spain", "Addr 123", 100,
            PropertyType.APARTMENT,
            new com.nestorria.server.modules.properties.embeddable.PriceDetails(1000, 200000),
            new com.nestorria.server.modules.properties.embeddable.FacilityDetails(2, 1, 1),
            List.of(),
            new com.nestorria.server.modules.properties.embeddable.PropertyLocation(40.4, -3.7, null, null)
        );
        testProperty.setId("prop-1");
    }

    @Test
    void getAverageRatings_ReturnsAggregatesForMultipleProperties() {
        List<String> propertyIds = List.of("prop-1", "prop-2");
        List<Object[]> aggregates = List.of(
            new Object[]{"prop-1", 4.5, 10L},
            new Object[]{"prop-2", 3.8, 5L}
        );

        when(reviewRepository.findRatingAggregatesByPropertyIds(propertyIds)).thenReturn(aggregates);

        Map<String, RatingAggregate> result = reviewService.getAverageRatings(propertyIds);

        assertEquals(2, result.size());
        assertEquals(4.5, result.get("prop-1").averageRating());
        assertEquals(10, result.get("prop-1").reviewCount());
        assertEquals(3.8, result.get("prop-2").averageRating());
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

    @Test
    void getAverageRatings_PropertiesWithNoReviews_AreExcluded() {
        List<String> propertyIds = List.of("prop-1");
        when(reviewRepository.findRatingAggregatesByPropertyIds(propertyIds)).thenReturn(List.of());

        Map<String, RatingAggregate> result = reviewService.getAverageRatings(propertyIds);

        assertTrue(result.isEmpty());
    }
}
