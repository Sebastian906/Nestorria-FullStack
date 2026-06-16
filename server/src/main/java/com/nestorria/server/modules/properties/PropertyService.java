package com.nestorria.server.modules.properties;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.agency.AgencyRepository;
import com.nestorria.server.modules.properties.dto.CreatePropertyRequest;
import com.nestorria.server.modules.properties.dto.PropertyResponse;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;
import com.nestorria.server.modules.properties.dto.ToggleAvailabilityRequest;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;

@Service
public class PropertyService {

    private static final int MAX_IMAGES = 4;

    private final PropertyRepository propertyRepository;
    private final AgencyRepository agencyRepository;
    private final Cloudinary cloudinary;

    public PropertyService(
            PropertyRepository propertyRepository,
            AgencyRepository agencyRepository,
            Cloudinary cloudinary) {
        this.propertyRepository = propertyRepository;
        this.agencyRepository = agencyRepository;
        this.cloudinary = cloudinary;
    }

    public PropertyResponse create(String userId, CreatePropertyRequest request, List<MultipartFile> files) {
        Agency agency = agencyRepository.findByOwnerId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró una agencia para este usuario"));
        List<String> imageUrls = uploadImages(files);
        return persistProperty(agency, request, imageUrls);
    }

    @Transactional
    protected PropertyResponse persistProperty(Agency agency, CreatePropertyRequest request, List<String> imageUrls) {
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

    @Transactional(readOnly = true)
    public List<PropertySummaryResponse> getAllAvailable() {
        return propertyRepository.findByIsAvailableTrue()
            .stream()
            .map(PropertySummaryResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PropertyResponse> getOwnerProperties(String userId) {
        Agency agency = agencyRepository.findByOwnerId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("No se encontró una agencia para este usuario"));

        return propertyRepository.findByAgencyId(agency.getId())
            .stream()
            .map(PropertyResponse::fromEntity)
            .toList();
    }

    @Transactional
    public void toggleAvailability(String userId, ToggleAvailabilityRequest request) {
        Agency agency = agencyRepository.findByOwnerId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("No se encontró una agencia para este usuario"));

        Property property = propertyRepository.findById(request.propertyId())
            .orElseThrow(() -> new ResourceNotFoundException("Propiedad no encontrada: " + request.propertyId()));

        // Verificación de ownership que el tutorial no hace
        if (!property.getAgency().getId().equals(agency.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes permiso para modificar esta propiedad"
            );
        }

        property.toggleAvailability();
        // No hace falta save() explícito — el contexto transaccional detecta el cambio (dirty checking)
    }

    private List<String> uploadImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<MultipartFile> limited = files.size() > MAX_IMAGES
            ? files.subList(0, MAX_IMAGES)
            : files;

        return limited.stream()
            .map(this::uploadSingle)
            .toList();
    }

    @SuppressWarnings("unchecked")
    private String uploadSingle(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen está vacío");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Solo se permiten archivos de imagen");
        }
        try {
            Map<String, Object> result = cloudinary.uploader().upload(
                file.getBytes(),
                Map.of(
                    "folder", "nestorria/properties",
                    "resource_type", "image"
                )
            );
            return (String) result.get("secure_url");
        } catch (IOException e) {
            throw new RuntimeException("Error al subir imagen a Cloudinary: " + e.getMessage(), e);
        }
    }
}
