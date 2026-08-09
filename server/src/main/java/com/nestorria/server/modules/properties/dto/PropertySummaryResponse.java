package com.nestorria.server.modules.properties.dto;

import java.time.Instant;
import java.util.List;

import com.nestorria.server.modules.agency.dto.AgencyResponse;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyType;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.properties.embeddable.PropertyLocation;

public record PropertySummaryResponse(
    String id,
    String title,
    String description,
    String city,
    String country,
    String address,
    int area,
    PropertyType propertyType,
    PriceDetails price,
    FacilityDetails facilities,
    List<String> amenities,
    List<String> images,
    boolean isAvailable,
    PropertyLocation location,
    AgencyResponse agency,
    Double averageRating,
    int reviewCount,
    Instant createdAt
) {
    public static PropertySummaryResponse fromEntity(Property p) {
        return fromEntity(p, null, 0);
    }

    public static PropertySummaryResponse fromEntity(Property p, Double averageRating, int reviewCount) {
        return new PropertySummaryResponse(
            p.getId(),
            p.getTitle(),
            p.getDescription(),
            p.getCity(),
            p.getCountry(),
            p.getAddress(),
            p.getArea(),
            p.getPropertyType(),
            p.getPrice(),
            p.getFacilities(),
            p.getAmenities(),
            p.getImages(),
            p.isAvailable(),
            p.getLocation(),
            AgencyResponse.fromEntity(p.getAgency()),
            averageRating,
            reviewCount,
            p.getCreatedAt()
        );
    }
}
