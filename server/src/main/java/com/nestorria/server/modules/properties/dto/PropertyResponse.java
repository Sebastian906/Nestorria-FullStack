package com.nestorria.server.modules.properties.dto;

import java.util.List;

import com.nestorria.server.modules.agency.dto.AgencyResponse;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyType;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.properties.embeddable.PropertyLocation;

public record PropertyResponse(
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
    AgencyResponse agency
) {
    public static PropertyResponse fromEntity(Property p) {
        return new PropertyResponse(
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
            AgencyResponse.fromEntity(p.getAgency())
        );
    }
}
