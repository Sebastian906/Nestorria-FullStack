package com.nestorria.server.modules.properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.cloudinary.Cloudinary;
import com.github.benmanes.caffeine.cache.Cache;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.agency.AgencyRepository;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;
import com.nestorria.server.modules.properties.dto.ToggleAvailabilityRequest;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.properties.embeddable.PropertyLocation;
import com.nestorria.server.modules.review.ReviewService;
import com.nestorria.server.modules.review.ReviewService.RatingAggregate;
import com.nestorria.server.modules.user.User;

@SpringBootTest
class PropertyServiceCacheTest {

    @Autowired private PropertyService propertyService;
    @Autowired private CacheManager cacheManager;

    @MockitoBean private PropertyRepository propertyRepository;
    @MockitoBean private AgencyRepository agencyRepository;
    @MockitoBean private Cloudinary cloudinary;
    @MockitoBean private PropertyPersistenceService persistenceService;
    @MockitoBean private ReviewService reviewService;

    private Property testProperty;
    private Agency mockAgency;

    private record CacheStats(long hitCount, long missCount) {}

    private CacheStats snapshotStats(String cacheName) {
        CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache(cacheName);
        Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
        return new CacheStats(nativeCache.stats().hitCount(), nativeCache.stats().missCount());
    }

    private void clearCache(String cacheName) {
        cacheManager.getCache(cacheName).clear();
    }

    @BeforeEach
    void setUp() {
        // Clear all caches between tests to prevent cross-test contamination
        clearCache("propertyListings");
        clearCache("ownerProperties");
        clearCache("propertyStats");

        User mockOwner = mock(User.class);
        when(mockOwner.getId()).thenReturn("owner-1");
        when(mockOwner.getImage()).thenReturn("http://img.url/avatar.jpg");

        mockAgency = mock(Agency.class);
        when(mockAgency.getId()).thenReturn("agency-1");
        when(mockAgency.getName()).thenReturn("Test Agency");
        when(mockAgency.getAddress()).thenReturn("Calle Agency 1");
        when(mockAgency.getContact()).thenReturn("123456");
        when(mockAgency.getEmail()).thenReturn("agency@test.com");
        when(mockAgency.getCity()).thenReturn("Madrid");
        when(mockAgency.getOwner()).thenReturn(mockOwner);

        testProperty = new Property(
            mockAgency, "Test Property", "Description", "Madrid", "Spain",
            "Calle Test 123", 100,
            PropertyType.APARTMENT,
            new PriceDetails(1000, 200000),
            new FacilityDetails(2, 1, 1),
            List.of("pool"),
            new PropertyLocation(40.4, -3.7, "Centro", "28001")
        );
        testProperty.setId("prop-1");
        testProperty.setImages(List.of("http://img.url/photo1.jpg"));
    }

    // ==================== getAllAvailable ====================

    @Test
    void getAllAvailable_CacheMiss_ThenCacheHit() {
        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of(testProperty));
        when(reviewService.getAverageRatings(any()))
            .thenReturn(Map.of("prop-1", new RatingAggregate(4.5, 10)));

        CacheStats before = snapshotStats("propertyListings");
        propertyService.getAllAvailable();
        CacheStats afterFirst = snapshotStats("propertyListings");
        propertyService.getAllAvailable();
        CacheStats afterSecond = snapshotStats("propertyListings");

        assertEquals(before.missCount() + 1, afterFirst.missCount());
        assertEquals(afterFirst.missCount(), afterSecond.missCount());
        assertEquals(afterFirst.hitCount() + 1, afterSecond.hitCount());
    }

    @Test
    void getAllAvailable_IncludesRatingsInResponse() {
        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of(testProperty));
        when(reviewService.getAverageRatings(any()))
            .thenReturn(Map.of("prop-1", new RatingAggregate(4.5, 10)));

        PropertySummaryResponse response = propertyService.getAllAvailable().getFirst();

        assertEquals(4.5, response.averageRating());
        assertEquals(10, response.reviewCount());
    }

    @Test
    void getAllAvailable_HandlesNullRatings() {
        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of(testProperty));
        when(reviewService.getAverageRatings(any())).thenReturn(Map.of());

        PropertySummaryResponse response = propertyService.getAllAvailable().getFirst();

        assertNull(response.averageRating());
        assertEquals(0, response.reviewCount());
    }

    // ==================== getOwnerProperties ====================

    @Test
    void getOwnerProperties_CacheMiss_ThenCacheHit() {
        when(agencyRepository.findByOwnerId("owner-1")).thenReturn(java.util.Optional.of(mockAgency));
        when(propertyRepository.findByAgencyId("agency-1")).thenReturn(List.of(testProperty));
        when(reviewService.getAverageRatings(any())).thenReturn(Map.of());

        CacheStats before = snapshotStats("ownerProperties");
        propertyService.getOwnerProperties("owner-1");
        CacheStats afterFirst = snapshotStats("ownerProperties");
        propertyService.getOwnerProperties("owner-1");
        CacheStats afterSecond = snapshotStats("ownerProperties");

        assertEquals(before.missCount() + 1, afterFirst.missCount());
        assertEquals(afterFirst.missCount(), afterSecond.missCount());
        assertEquals(afterFirst.hitCount() + 1, afterSecond.hitCount());
    }

    // ==================== toggleAvailability evicts ====================

    @Test
    void toggleAvailability_EvictsPropertyListingsCache() {
        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of(testProperty));
        when(reviewService.getAverageRatings(any())).thenReturn(Map.of());
        when(agencyRepository.findByOwnerId("owner-1")).thenReturn(java.util.Optional.of(mockAgency));
        when(propertyRepository.findById("prop-1")).thenReturn(java.util.Optional.of(testProperty));

        // Populate cache
        propertyService.getAllAvailable();
        CacheStats afterPopulate = snapshotStats("propertyListings");

        // Evict
        propertyService.toggleAvailability("owner-1", new ToggleAvailabilityRequest("prop-1"));

        // After eviction: getAllAvailable misses again
        propertyService.getAllAvailable();
        CacheStats afterEvict = snapshotStats("propertyListings");

        assertEquals(afterPopulate.missCount() + 1, afterEvict.missCount());
    }
}
