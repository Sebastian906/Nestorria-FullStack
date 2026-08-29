package com.nestorria.server.common.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.ai.dto.ToolBookingStatsResponse;
import com.nestorria.server.common.ai.dto.ToolPropertyAvgPriceResponse;
import com.nestorria.server.common.ai.dto.ToolPropertyCountResponse;
import com.nestorria.server.common.ai.dto.ToolPropertySearchResponse;
import com.nestorria.server.common.ai.dto.ToolPropertySearchResponse.ToolPropertySummary;
import com.nestorria.server.common.ai.dto.ToolReviewAverageResponse;
import com.nestorria.server.modules.booking.BookingRepository;
import com.nestorria.server.modules.booking.BookingStatus;
import com.nestorria.server.modules.properties.PropertySearchService;
import com.nestorria.server.modules.review.ReviewRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Service interno para ejecutar consultas de herramientas del LLM.
 * Reutiliza servicios/repositorios existentes. Solo lectura.
 * Todas las queries usan @ReadFromReplica cuando es posible
 * para no cargar la primary innecesariamente.
 */
@Service
@Slf4j
public class AiToolService {

    private final PropertySearchService propertySearchService;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;

    public AiToolService(
            PropertySearchService propertySearchService,
            BookingRepository bookingRepository,
            ReviewRepository reviewRepository) {
        this.propertySearchService = propertySearchService;
        this.bookingRepository = bookingRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public ToolPropertyCountResponse getPropertyCount(String city, String propertyType) {
        List<ToolPropertySummary> results = searchPropertiesRaw(city, propertyType, null, null);

        Map<String, String> filters = new LinkedHashMap<>();
        if (city != null && !city.isBlank()) filters.put("city", city);
        if (propertyType != null && !propertyType.isBlank()) filters.put("propertyType", propertyType);

        log.info("tool_property_count: count={}, filters={}", results.size(), filters);
        return new ToolPropertyCountResponse(results.size(), filters);
    }

    @Transactional(readOnly = true)
    public ToolPropertyAvgPriceResponse getAveragePrice(String city, String propertyType) {
        List<ToolPropertySummary> results = searchPropertiesRaw(city, propertyType, null, null);

        if (results.isEmpty()) {
            return new ToolPropertyAvgPriceResponse(0.0, 0);
        }

        double avg = results.stream()
            .mapToInt(p -> p.salePrice() != null ? p.salePrice() : 0)
            .filter(p -> p > 0)
            .average()
            .orElse(0.0);

        log.info("tool_property_avg_price: avg={}, count={}", avg, results.size());
        return new ToolPropertyAvgPriceResponse(Math.round(avg * 100.0) / 100.0, results.size());
    }

    @Transactional(readOnly = true)
    public ToolPropertySearchResponse searchProperties(
            String city, String propertyType, Integer minPrice, Integer maxPrice) {

        List<ToolPropertySummary> results = searchPropertiesRaw(city, propertyType, minPrice, maxPrice);

        // Limit to 10 results for LLM consumption
        List<ToolPropertySummary> limited = results.size() > 10
            ? results.subList(0, 10)
            : results;

        log.info("tool_property_search: city={}, type={}, minPrice={}, maxPrice={}, results={}",
            city, propertyType, minPrice, maxPrice, limited.size());
        return new ToolPropertySearchResponse(limited, limited.size());
    }

    private List<ToolPropertySummary> searchPropertiesRaw(
            String city, String propertyType, Integer minPrice, Integer maxPrice) {

        return propertySearchService.findByFilters(
                city, propertyType, minPrice, maxPrice,
                null, null, null, null)
            .stream()
            .map(p -> new ToolPropertySummary(
                p.id(),
                p.title(),
                p.city(),
                p.propertyType().getDisplayName(),
                p.price() != null ? p.price().getSale() : null,
                p.price() != null ? p.price().getRent() : null,
                p.area(),
                p.address()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public ToolBookingStatsResponse getBookingStats() {
        long confirmed = bookingRepository.countByStatus(BookingStatus.CONFIRMED);
        long pending = bookingRepository.countByStatus(BookingStatus.PENDING);
        long cancelled = bookingRepository.countByStatus(BookingStatus.CANCELLED);
        long total = confirmed + pending + cancelled;

        // Revenue from confirmed bookings that are paid
        long revenue = bookingRepository.sumRevenueByPaidConfirmed();

        log.info("tool_booking_stats: total={}, confirmed={}, revenue={}", total, confirmed, revenue);
        return new ToolBookingStatsResponse(total, confirmed, pending, cancelled, revenue);
    }

    @Transactional(readOnly = true)
    public ToolReviewAverageResponse getReviewAverage(String propertyId) {
        List<Object[]> aggregates = reviewRepository.findRatingAggregatesByPropertyIds(List.of(propertyId));

        if (aggregates.isEmpty()) {
            return new ToolReviewAverageResponse(0.0, 0, propertyId);
        }

        Object[] row = aggregates.get(0);
        double avgRating = (Double) row[1];
        long count = (Long) row[2];

        log.info("tool_review_average: propertyId={}, avg={}, count={}", propertyId, avgRating, count);
        return new ToolReviewAverageResponse(
            Math.round(avgRating * 10.0) / 10.0,
            (int) count,
            propertyId
        );
    }
}
