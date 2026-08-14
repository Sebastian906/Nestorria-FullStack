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
import com.nestorria.server.common.algorithm.SearchUtils;
import com.nestorria.server.common.exception.BadRequestException;
import com.nestorria.server.common.exception.ConflictException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.agency.AgencyRepository;
import com.nestorria.server.modules.properties.dto.CreatePropertyRequest;
import com.nestorria.server.modules.properties.dto.PropertyResponse;
import com.nestorria.server.modules.properties.dto.PropertyStatsResponse;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;
import com.nestorria.server.modules.properties.dto.ToggleAvailabilityRequest;
import com.nestorria.server.modules.review.ReviewService;
import com.nestorria.server.modules.review.ReviewService.RatingAggregate;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PropertyService {

    private static final int MAX_IMAGES = 4;

    private final PropertyRepository propertyRepository;
    private final AgencyRepository agencyRepository;
    private final Cloudinary cloudinary;
    private final PropertyPersistenceService persistenceService;
    private final PropertyListingService listingService;
    private final ReviewService reviewService;
    private final Executor imageUploadTaskExecutor;

    public PropertyService(
            PropertyRepository propertyRepository,
            AgencyRepository agencyRepository,
            Cloudinary cloudinary,
            PropertyPersistenceService persistenceService,
            PropertyListingService listingService,
            ReviewService reviewService,
            @Qualifier("imageUploadTaskExecutor") Executor imageUploadTaskExecutor) {
        this.propertyRepository = propertyRepository;
        this.agencyRepository = agencyRepository;
        this.cloudinary = cloudinary;
        this.persistenceService = persistenceService;
        this.listingService = listingService;
        this.reviewService = reviewService;
        this.imageUploadTaskExecutor = imageUploadTaskExecutor;
    }

    @CacheEvict(cacheNames = {"propertyListings", "ownerProperties", "propertyStats"}, allEntries = true)
    public PropertyResponse create(String userId, CreatePropertyRequest request, List<MultipartFile> files) {
        Agency agency = agencyRepository.findByOwnerId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró una agencia para este usuario"));
        List<String> imageUrls = uploadImagesParallel(files);
        return persistenceService.persistProperty(agency, request, imageUrls);
    }

    /**
     * Delega a PropertyListingService para que el proxy de Spring intercepte
     * la llamada y el @Cacheable funcione correctamente.
     */
    public List<PropertySummaryResponse> getAllAvailable() {
        return listingService.getAllAvailable();
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

    @CacheEvict(cacheNames = {"propertyListings", "ownerProperties", "propertyStats"}, allEntries = true)
    @Transactional
    public void toggleAvailability(String userId, ToggleAvailabilityRequest request) {
        Agency agency = agencyRepository.findByOwnerId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró una agencia para este usuario"));

        Property property = propertyRepository.findById(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Propiedad no encontrada: " + request.propertyId()));

        if (!property.getAgency().getId().equals(agency.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No tienes permiso para modificar esta propiedad");
        }

        property.toggleAvailability();
    }

    /**
     * Estadísticas de propiedades usando SearchUtils sobre datos cacheados.
     * Reutiliza getAllAvailable() que ya tiene @Cacheable.
     * Complejidad: O(n) sobre la lista cacheada, sin queries adicionales a DB.
     */
    @Cacheable(cacheNames = "propertyStats", key = "'global'")
    @Transactional(readOnly = true)
    public PropertyStatsResponse getPropertyStats() {
        List<PropertySummaryResponse> properties = listingService.getAllAvailable();

        Map<String, Long> byType = SearchUtils.countBy(
            properties,
            p -> p.propertyType().getDisplayName()
        );

        Map<String, Long> byCity = SearchUtils.countBy(
            properties,
            PropertySummaryResponse::city
        );

        return new PropertyStatsResponse(
            properties.size(),
            byType,
            byCity
        );
    }

    /**
     * Sube imágenes a Cloudinary en paralelo usando CompletableFuture.
     * Mantiene el contrato de API: la respuesta incluye las URLs.
     * Usa imageUploadTaskExecutor (pool dedicado I/O-bound) para no competir
     * con emails (SMTP) en el mismo pool.
     */
    private List<String> uploadImagesParallel(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<MultipartFile> limited = files.size() > MAX_IMAGES
            ? files.subList(0, MAX_IMAGES)
            : files;

        List<CompletableFuture<String>> futures = limited.stream()
            .map(file -> CompletableFuture.supplyAsync(
                () -> uploadSingle(file), imageUploadTaskExecutor))
                .toList();

        List<String> urls = new ArrayList<>(futures.size());
        List<String> uploadedUrls = new ArrayList<>();
        try {
            for (CompletableFuture<String> future : futures) {
                urls.add(future.join());
            }
            uploadedUrls.addAll(urls);
        } catch (Exception e) {
            // Compensación: eliminar imágenes que ya se subieron antes del fallo
            log.warn("Fallo en subida paralela, eliminando {} imágenes previas", uploadedUrls.size());
            uploadedUrls.forEach(this::deleteFromCloudinary);
            throw e;
        }

        return urls;
    }

    /**
     * Elimina una imagen previamente subida a Cloudinary (compensación ante fallo parcial).
     * No lanza excepciones — si falla la eliminación, solo registra warning.
     */
    private void deleteFromCloudinary(String url) {
        try {
            String prefix = "/upload/";
            int start = url.indexOf(prefix);
            int lastSlash = url.lastIndexOf('/');
            if (start < 0 || lastSlash < 0) return;

            String path = url.substring(start + prefix.length(), lastSlash);
            // Remover prefijo de versión si existe (v1234567890/)
            if (path.matches("^v\\d+/.+")) {
                path = path.substring(path.indexOf('/') + 1);
            }

            cloudinary.uploader().destroy(path, Map.of("resource_type", "image"));
            log.info("Imagen huérfana eliminada de Cloudinary: {}", path);
        } catch (Exception e) {
            log.warn("No se pudo eliminar imagen huérfana de Cloudinary: {} — {}", url, e.getMessage());
        }
    }

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
