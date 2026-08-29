package com.nestorria.server.common.ai;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nestorria.server.common.ai.dto.ToolBookingStatsResponse;
import com.nestorria.server.common.ai.dto.ToolPropertyAvgPriceResponse;
import com.nestorria.server.common.ai.dto.ToolPropertyCountResponse;
import com.nestorria.server.common.ai.dto.ToolPropertySearchResponse;
import com.nestorria.server.common.ai.dto.ToolReviewAverageResponse;
import com.nestorria.server.modules.booking.BookingRepository;
import com.nestorria.server.modules.booking.BookingStatus;
import com.nestorria.server.modules.properties.PropertyType;
import com.nestorria.server.modules.properties.PropertySearchService;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.properties.embeddable.PropertyLocation;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;
import com.nestorria.server.modules.review.ReviewRepository;

class AiToolServiceTest {

    private AiToolService toolService;
    private PropertySearchService propertySearchService;
    private BookingRepository bookingRepository;
    private ReviewRepository reviewRepository;

    @BeforeEach
    void setUp() {
        propertySearchService = mock(PropertySearchService.class);
        bookingRepository = mock(BookingRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        toolService = new AiToolService(propertySearchService, bookingRepository, reviewRepository);
    }

    // ── getPropertyCount ──────────────────────────────────────────────

    @Test
    void getPropertyCount_noFilters_returnsTotal() {
        when(propertySearchService.countByFilters(isNull(), isNull(), isNull(), isNull()))
            .thenReturn(3L);

        ToolPropertyCountResponse result = toolService.getPropertyCount(null, null);

        assertEquals(3, result.count());
        assertTrue(result.filters().isEmpty());
    }

    @Test
    void getPropertyCount_withCity_returnsFiltered() {
        when(propertySearchService.countByFilters(eq("Madrid"), isNull(), isNull(), isNull()))
            .thenReturn(2L);

        ToolPropertyCountResponse result = toolService.getPropertyCount("Madrid", null);

        assertEquals(2, result.count());
        assertEquals("Madrid", result.filters().get("city"));
    }

    // ── getAveragePrice ───────────────────────────────────────────────

    @Test
    void getAveragePrice_withResults_returnsCalculated() {
        when(propertySearchService.avgAndCountByFilters(isNull(), isNull(), isNull(), isNull()))
            .thenReturn(new Object[]{250000L, 2L});

        ToolPropertyAvgPriceResponse result = toolService.getAveragePrice(null, null);

        assertEquals(250000.0, result.averagePrice(), 1.0);
        assertEquals(2, result.count());
    }

    @Test
    void getAveragePrice_noResults_returnsZero() {
        when(propertySearchService.avgAndCountByFilters(isNull(), isNull(), isNull(), isNull()))
            .thenReturn(new Object[]{null, null});

        ToolPropertyAvgPriceResponse result = toolService.getAveragePrice(null, null);

        assertEquals(0.0, result.averagePrice());
        assertEquals(0, result.count());
    }

    // ── searchProperties ──────────────────────────────────────────────

    @Test
    void searchProperties_returnsLimitedResults() {
        List<PropertySummaryResponse> ten = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ten.add(mockSummary("p" + i));
        }
        when(propertySearchService.findByFiltersWithLimit(
            eq("Madrid"), isNull(), isNull(), isNull(), eq(10)))
            .thenReturn(ten);

        ToolPropertySearchResponse result = toolService.searchProperties("Madrid", null, null, null);

        assertEquals(10, result.properties().size());
        assertEquals(10, result.count());
    }

    // ── getBookingStats ───────────────────────────────────────────────

    @Test
    void getBookingStats_returnsAggregatedStats() {
        when(bookingRepository.countByStatus(BookingStatus.CONFIRMED)).thenReturn(10L);
        when(bookingRepository.countByStatus(BookingStatus.PENDING)).thenReturn(3L);
        when(bookingRepository.countByStatus(BookingStatus.CANCELLED)).thenReturn(2L);
        when(bookingRepository.sumRevenueByPaidConfirmed()).thenReturn(75000L);

        ToolBookingStatsResponse result = toolService.getBookingStats();

        assertEquals(15L, result.totalBookings());
        assertEquals(10L, result.confirmedBookings());
        assertEquals(3L, result.pendingBookings());
        assertEquals(2L, result.cancelledBookings());
        assertEquals(75000L, result.totalRevenue());
    }

    // ── getReviewAverage ──────────────────────────────────────────────

    @Test
    void getReviewAverage_withReviews_returnsAggregated() {
        Object[] row = new Object[]{"prop-123", 4.5, 3L};
        when(reviewRepository.findRatingAggregatesByPropertyIds(List.of("prop-123")))
            .thenReturn(List.<Object[]>of(row));

        ToolReviewAverageResponse result = toolService.getReviewAverage("prop-123");

        assertEquals(4.5, result.averageRating(), 0.1);
        assertEquals(3, result.reviewCount());
        assertEquals("prop-123", result.propertyId());
    }

    @Test
    void getReviewAverage_noReviews_returnsZero() {
        when(reviewRepository.findRatingAggregatesByPropertyIds(List.of("prop-none")))
            .thenReturn(List.of());

        ToolReviewAverageResponse result = toolService.getReviewAverage("prop-none");

        assertEquals(0.0, result.averageRating());
        assertEquals(0, result.reviewCount());
    }

    // ── helpers ───────────────────────────────────────────────────────

    private static final PriceDetails EMPTY_PRICE = new PriceDetails();
    private static final FacilityDetails EMPTY_FACILITIES = new FacilityDetails();
    private static final PropertyLocation EMPTY_LOCATION = new PropertyLocation();

    private PropertySummaryResponse mockSummary(String id) {
        return new PropertySummaryResponse(
            id, "Title " + id, "Desc " + id, "Madrid", "Spain",
            "Address " + id, 100, PropertyType.HOUSE, EMPTY_PRICE,
            EMPTY_FACILITIES, List.of(), List.of(), true,
            EMPTY_LOCATION, null, null, 0, null
        );
    }
}
