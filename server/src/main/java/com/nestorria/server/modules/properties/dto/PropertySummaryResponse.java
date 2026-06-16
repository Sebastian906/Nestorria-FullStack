package com.nestorria.server.modules.properties.dto;

import java.util.List;

import com.nestorria.server.modules.agency.dto.AgencyResponse;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyType;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;

public record PropertySummaryResponse(
    String id,
    String title,
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
    AgencyResponse agency
) {
    public static PropertySummaryResponse fromEntity(Property p) {
        return new PropertySummaryResponse(
            p.getId(),
            p.getTitle(),
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
            AgencyResponse.fromEntity(p.getAgency())
        );
    }
}
