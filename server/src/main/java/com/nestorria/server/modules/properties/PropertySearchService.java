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
}
