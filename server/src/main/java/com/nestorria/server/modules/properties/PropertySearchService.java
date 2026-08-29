package com.nestorria.server.modules.properties;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PropertySearchService {

    private final PropertySearchRepository propertySearchRepository;
    private final CategoryService categoryService;

    @Transactional(readOnly = true)
    public List<PropertySummaryResponse> findNearby(Double lat, Double lng, Double radiusKm) {
        double radiusMeters = radiusKm * 1000;
        return propertySearchRepository.findNearby(lat, lng, radiusMeters)
            .stream()
            .map(PropertySummaryResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PropertySummaryResponse> findByFilters(
            String city, String propertyType, Integer minPrice, Integer maxPrice,
            Long categoryId, Double lat, Double lng, Double radiusKm) {

        Set<Long> categoryIds = null;
        if (categoryId != null) {
            categoryIds = categoryService.getDescendantIds(categoryId);
        }

        if (lat != null && lng != null && radiusKm != null) {
            return propertySearchRepository.findNearbyWithFilters(
                lat, lng, radiusKm * 1000, city, propertyType, minPrice, maxPrice, categoryIds
            ).stream().map(PropertySummaryResponse::fromEntity).toList();
        }
        return propertySearchRepository.findByFilters(city, propertyType, minPrice, maxPrice, categoryIds)
            .stream().map(PropertySummaryResponse::fromEntity).toList();
    }

    // ── Tool-specific aggregate methods (avoid materializing all results) ──

    private Set<Long> resolveCategoryIds(Long categoryId) {
        if (categoryId != null) {
            return categoryService.getDescendantIds(categoryId);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public long countByFilters(String city, String propertyType, Integer minPrice, Integer maxPrice) {
        return propertySearchRepository.countByFilters(city, propertyType, minPrice, maxPrice, null);
    }

    /**
     * Returns [avgPrice, count] from the database. Both are 0 when no valid prices exist.
     */
    @Transactional(readOnly = true)
    public Object[] avgAndCountByFilters(String city, String propertyType, Integer minPrice, Integer maxPrice) {
        return propertySearchRepository.avgAndCountByFilters(city, propertyType, minPrice, maxPrice, null);
    }

    @Transactional(readOnly = true)
    public List<PropertySummaryResponse> findByFiltersWithLimit(
            String city, String propertyType, Integer minPrice, Integer maxPrice, int limit) {
        return propertySearchRepository.findByFiltersWithLimit(city, propertyType, minPrice, maxPrice, null, limit)
            .stream().map(PropertySummaryResponse::fromEntity).toList();
    }
}
