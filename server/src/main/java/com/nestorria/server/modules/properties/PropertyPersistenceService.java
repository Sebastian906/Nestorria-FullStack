package com.nestorria.server.modules.properties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.properties.dto.CreatePropertyRequest;
import com.nestorria.server.modules.properties.dto.PropertyResponse;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.properties.embeddable.PropertyLocation;

@Service
public class PropertyPersistenceService {

    private final PropertyRepository propertyRepository;

    public PropertyPersistenceService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public PropertyResponse persistProperty(Agency agency, CreatePropertyRequest request, List<String> imageUrls) {
        PropertyLocation location = null;
        if (request.latitude() != null && request.longitude() != null) {
            location = new PropertyLocation(
                    request.latitude(),
                    request.longitude(),
                    request.neighborhood(),
                    request.postalCode());
        }

        List<String> amenities = request.amenities() != null
            ? new ArrayList<>(new LinkedHashSet<>(request.amenities()))
            : new ArrayList<>();


        Property property = new Property(
            agency,
            request.title(),
            request.description(),
            request.city(),
            request.country(),
            request.address(),
            request.area(),
            request.propertyType(),
            new PriceDetails(request.priceRent(), request.priceSale()),
            new FacilityDetails(request.bedrooms(), request.bathrooms(), request.garages()),
            amenities,
            location
        );
        property.setImages(imageUrls);
        return PropertyResponse.fromEntity(propertyRepository.save(property));
    }
}
