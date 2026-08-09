package com.nestorria.server.modules.properties;

import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;
import com.nestorria.server.modules.review.ReviewService;
import com.nestorria.server.modules.review.ReviewService.RatingAggregate;

/**
 * Bean separado que expone getAllAvailable() con @Cacheable.
 * Separado de PropertyService para evitar el problema de self-invocation
 * de Spring: cuando un método @Cacheable llama a otro @Cacheable dentro
 * de la misma clase, el proxy de Spring no intercepta la llamada interna
 * y el cache no funciona.
 */
@Service
public class PropertyListingService {

    private final PropertyRepository propertyRepository;
    private final ReviewService reviewService;

    public PropertyListingService(PropertyRepository propertyRepository, ReviewService reviewService) {
        this.propertyRepository = propertyRepository;
        this.reviewService = reviewService;
    }

    @Cacheable(cacheNames = "propertyListings", key = "'all-available'")
    @Transactional(readOnly = true)
    public List<PropertySummaryResponse> getAllAvailable() {
        List<Property> properties = propertyRepository.findByIsAvailableTrue();

        List<String> propertyIds = properties.stream()
            .map(Property::getId)
            .toList();

        Map<String, RatingAggregate> ratings = reviewService.getAverageRatings(propertyIds);

        return properties.stream()
            .map(p -> {
                RatingAggregate agg = ratings.get(p.getId());
                Double avgRating = agg != null ? agg.averageRating() : null;
                int reviewCount = agg != null ? agg.reviewCount() : 0;
                return PropertySummaryResponse.fromEntity(p, avgRating, reviewCount);
            })
            .toList();
    }
}
