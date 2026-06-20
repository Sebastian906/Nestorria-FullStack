package com.nestorria.server.modules.properties;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.properties.dto.CreatePropertyRequest;
import com.nestorria.server.modules.properties.dto.PropertyResponse;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;

@Service
public class PropertyPersistenceService {

    private final PropertyRepository propertyRepository;

    public PropertyPersistenceService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public PropertyResponse persistProperty(Agency agency, CreatePropertyRequest request, List<String> imageUrls) {
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
            request.amenities()
        );
        property.setImages(imageUrls);
        return PropertyResponse.fromEntity(propertyRepository.save(property));
    }
}
