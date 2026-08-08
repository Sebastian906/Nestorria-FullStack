package com.nestorria.server.modules.properties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.nestorria.server.common.exception.BadRequestException;
import com.nestorria.server.common.exception.ConflictException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.agency.AgencyRepository;
import com.nestorria.server.modules.properties.dto.CreatePropertyRequest;
import com.nestorria.server.modules.properties.dto.PropertyResponse;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;
import com.nestorria.server.modules.properties.dto.ToggleAvailabilityRequest;
import com.nestorria.server.modules.review.ReviewService;
import com.nestorria.server.modules.review.ReviewService.RatingAggregate;

@Service
public class PropertyService {

    private static final int MAX_IMAGES = 4;

    private final PropertyRepository propertyRepository;
    private final AgencyRepository agencyRepository;
    private final Cloudinary cloudinary;
    private final PropertyPersistenceService persistenceService;
    private final ReviewService reviewService;
    private final Executor emailTaskExecutor;

    public PropertyService(
            PropertyRepository propertyRepository,
            AgencyRepository agencyRepository,
            Cloudinary cloudinary,
            PropertyPersistenceService persistenceService,
            ReviewService reviewService,
            @Qualifier("emailTaskExecutor") Executor emailTaskExecutor) {
        this.propertyRepository = propertyRepository;
        this.agencyRepository = agencyRepository;
        this.cloudinary = cloudinary;
        this.persistenceService = persistenceService;
        this.reviewService = reviewService;
        this.emailTaskExecutor = emailTaskExecutor;
    }

    @CacheEvict(cacheNames = {"propertyListings", "ownerProperties"}, allEntries = true)
    public PropertyResponse create(String userId, CreatePropertyRequest request, List<MultipartFile> files) {
        Agency agency = agencyRepository.findByOwnerId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró una agencia para este usuario"));
        List<String> imageUrls = uploadImagesParallel(files);
        return persistenceService.persistProperty(agency, request, imageUrls);
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

    @Cacheable(cacheNames = "ownerProperties", key = "#userId")
    @Transactional(readOnly = true)
    public List<PropertyResponse> getOwnerProperties(String userId) {
        Agency agency = agencyRepository.findByOwnerId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("No se encontró una agencia para este usuario"));

        List<Property> properties = propertyRepository.findByAgencyId(agency.getId());

        List<String> propertyIds = properties.stream()
            .map(Property::getId)
            .toList();

        Map<String, RatingAggregate> ratings = reviewService.getAverageRatings(propertyIds);

        return properties.stream()
            .map(p -> {
                RatingAggregate agg = ratings.get(p.getId());
                Double avgRating = agg != null ? agg.averageRating() : null;
                int reviewCount = agg != null ? agg.reviewCount() : 0;
                return PropertyResponse.fromEntity(p, avgRating, reviewCount);
            })
            .toList();
    }

    @CacheEvict(cacheNames = {"propertyListings", "ownerProperties"}, allEntries = true)
    @Transactional
    public void toggleAvailability(String userId, ToggleAvailabilityRequest request) {
        Agency agency = agencyRepository.findByOwnerId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("No se encontró una agencia para este usuario"));

        Property property = propertyRepository.findById(request.propertyId())
            .orElseThrow(() -> new ResourceNotFoundException("Propiedad no encontrada: " + request.propertyId()));

        if (!property.getAgency().getId().equals(agency.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes permiso para modificar esta propiedad"
            );
        }

        property.toggleAvailability();
    }

    /**
     * Sube imágenes a Cloudinary en paralelo usando CompletableFuture.
     * Mantiene el contrato de API: la respuesta incluye las URLs.
     * La diferencia con el enfoque secuencial es que las 4 imágenes
     * se suben concurrentemente, reduciendo la latencia total.
     *
     * NOTA: Esto NO usa @Async porque las URLs son necesarias antes
     * de persistir la propiedad. Usa el emailTaskExecutor como pool
     * de threads para las subidas — es un pool I/O-bound que ya existe.
     */
    private List<String> uploadImagesParallel(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<MultipartFile> limited = files.size() > MAX_IMAGES
            ? files.subList(0, MAX_IMAGES)
            : files;

        // Crear un CompletableFuture por cada imagen
        List<CompletableFuture<String>> futures = limited.stream()
            .map(file -> CompletableFuture.supplyAsync(
                () -> uploadSingle(file), emailTaskExecutor))
            .toList();

        // Esperar a que todas completen y recoger resultados
        // Si alguna falla, CompletableFuture.join() lanza CompletionException
        List<String> urls = new ArrayList<>(futures.size());
        for (CompletableFuture<String> future : futures) {
            urls.add(future.join());
        }

        return urls;
    }

    // Upload secuencial (método original, mantenido como fallback).
    @SuppressWarnings("unchecked")
    private String uploadSingle(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El archivo de imagen está vacío");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("Solo se permiten archivos de imagen");
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
            throw new ConflictException("Error al subir imagen a Cloudinary: " + e.getMessage());
        }
    }
}
