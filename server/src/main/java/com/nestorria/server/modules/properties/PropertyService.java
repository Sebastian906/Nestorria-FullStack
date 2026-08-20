package com.nestorria.server.modules.properties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.nestorria.server.common.algorithm.SearchUtils;
import com.nestorria.server.common.exception.BadRequestException;
import com.nestorria.server.common.exception.ConflictException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.common.util.UndoManager;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.agency.AgencyRepository;
import com.nestorria.server.modules.properties.PropertySortUtils.SortDirection;
import com.nestorria.server.modules.properties.PropertySortUtils.SortField;
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
        UndoManager undo = new UndoManager();
        try {
            List<UploadResult> uploaded = uploadImagesParallel(files);
            // Registrar compensación: si la persistencia falla, eliminar
            // las imágenes subidas (recursos externos no transaccionales).
            uploaded.forEach(r -> undo.push(() -> deleteFromCloudinary(r.publicId())));
            return persistenceService.persistProperty(
                agency, request, uploaded.stream().map(UploadResult::url).toList());
        } catch (RuntimeException e) {
            undo.undoAll();
            throw e;
        }
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
     * Divide-and-conquer: las estadísticas de precio incluyen mediana calculada
     * via quickselect O(n) average, en lugar de sort O(n log n).
     * Complejidad: O(n) sobre la lista cacheada, sin queries adicionales a DB.
     * La mediana agrega O(n) average via quickselect.
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

        // Divide-and-conquer: extraer precios y calcular estadísticas
        List<Integer> prices = properties.stream()
            .map(p -> p.price().getSale())
            .toList();
        PropertyStatsResponse.PriceStatistics priceStats = 
            PropertyStatsResponse.PriceStatistics.fromPrices(prices);

        return new PropertyStatsResponse(
            properties.size(),
            byType,
            byCity,
            priceStats
        );
    }

    /**
     * Sube imágenes a Cloudinary en paralelo usando CompletableFuture.
     * Mantiene el contrato de API: la respuesta incluye las URLs.
     * Usa imageUploadTaskExecutor (pool dedicado I/O-bound) para no competir
     * con emails (SMTP) en el mismo pool.
     */
    private List<UploadResult> uploadImagesParallel(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<MultipartFile> limited = files.size() > MAX_IMAGES
            ? files.subList(0, MAX_IMAGES)
            : files;

        List<CompletableFuture<UploadResult>> futures = limited.stream()
            .map(file -> CompletableFuture.supplyAsync(
                () -> uploadSingle(file), imageUploadTaskExecutor))
                .toList();

        List<UploadResult> uploaded = new ArrayList<>();
        RuntimeException firstFailure = null;
        for (CompletableFuture<UploadResult> future : futures) {
            try {
                uploaded.add(future.join());
            } catch (RuntimeException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }

        if (firstFailure != null) {
            // Compensación: esperar a que TODAS las subidas se asienten, luego
            // eliminar todas las que completaron (incluidas las que terminaron
            // después del primer fallo) y relanzar la primera excepción.
            log.warn("Fallo en subida paralela, eliminando {} imágenes previas", uploaded.size());
            uploaded.forEach(r -> deleteFromCloudinary(r.publicId()));
            throw firstFailure;
        }

        // Retorna UploadResult (url + publicId): la compensación interna
        // cubre fallos DURANTE la subida; el UndoManager de create() cubre
        // fallos posteriores (persistencia). Si falla la subida, este método
        // ya compensó y lanza antes de que el UndoManager tenga acciones.
        return uploaded;

    }

    private record UploadResult(String url, String publicId) {}

    /**
     * Elimina una imagen previamente subida a Cloudinary (compensación ante fallo parcial).
     * No lanza excepciones — si falla la eliminación, solo registra warning.
     */
    private void deleteFromCloudinary(String publicId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(
                publicId, Map.of("resource_type", "image"));

            String status = (String) result.get("result");
            if ("ok".equals(status)) {
                log.info("Imagen huérfana eliminada de Cloudinary: {}", publicId);
            } else {
                log.warn("Cloudinary destroy devolvió '{}' para publicId={}", status, publicId);
            }
        } catch (Exception e) {
            log.warn("No se pudo eliminar imagen huérfana de Cloudinary: {} — {}", publicId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private UploadResult uploadSingle(MultipartFile file) {
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
                            "resource_type", "image"));
            return new UploadResult(
                    (String) result.get("secure_url"),
                    (String) result.get("public_id"));
        } catch (IOException e) {
            throw new ConflictException("Error al subir imagen a Cloudinary: " + e.getMessage());
        }
    }

    /**
     * Paginación server-side sobre el dataset cacheado (getAllAvailable ya está
     * cacheado con Caffeine). Filtra -> ordena -> pagina EN MEMORIA.
     * El techo: el dataset completo se consulta una vez por ventana de cache (5 min).
     * Cuando la BD crezca a decenas de miles, mover filtro+LIMIT/OFFSET a SQL
     * sin cambiar este contrato (Page<PropertySummaryResponse>).
     */
    public Page<PropertySummaryResponse> getAvailablePage(Pageable pageable, PropertyPageFilter filter) {
        List<PropertySummaryResponse> all = listingService.getAllAvailable(); // proxy -> cache hit

        Comparator<PropertySummaryResponse> comparator = PropertySortUtils
            .getComparator(filter.sortBy(), filter.direction())
            .thenComparing(PropertySummaryResponse::id); // desempate determinista

        List<PropertySummaryResponse> sorted = all.stream()
            .filter(filter::matches)
            .sorted(comparator)
            .toList();

        int total = sorted.size();
        int from = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), total);
        int to = (int) Math.min((long) from + pageable.getPageSize(), total);
        List<PropertySummaryResponse> content = from < to ? sorted.subList(from, to) : List.of();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Filtros del listado. La semántica replica EXACTAMENTE la lógica del frontend
     * actual de Listing.tsx: tipos por displayName, rangos de precio sobre price.sale,
     * búsqueda sobre título/ciudad/país, favoritos por id (lista vacía = nada matchea).
     */
    public record PropertyPageFilter(
            List<String> propertyTypes,
            List<String> priceRanges,
            String q,
            Set<String> favoriteIds,
            SortField sortBy,
            SortDirection direction
    ) {
        public boolean matches(PropertySummaryResponse p) {
            if (propertyTypes != null && !propertyTypes.isEmpty()
                    && !propertyTypes.contains(p.propertyType().getDisplayName())) {
                return false;
            }
            if (priceRanges != null && !priceRanges.isEmpty() && !matchesPrice(p, priceRanges)) {
                return false;
            }
            if (q != null && !q.isBlank()) {
                String needle = q.toLowerCase().trim();
                boolean hit = (p.title() != null && p.title().toLowerCase().contains(needle))
                        || (p.city() != null && p.city().toLowerCase().contains(needle))
                        || (p.country() != null && p.country().toLowerCase().contains(needle));
                if (!hit) return false;
            }
            // Importante: favoriteIds presente (aunque vacío) = filtrar por favoritos.
            // Lista vacía -> ninguna propiedad matchea (igual que el frontend actual).
            if (favoriteIds != null && !favoriteIds.contains(p.id())) {
                return false;
            }
            return true;
        }

        private static boolean matchesPrice(PropertySummaryResponse p, List<String> ranges) {
            Integer sale = p.price() != null ? p.price().getSale() : null;
            if (sale == null) return false;
            for (String range : ranges) {
                try {
                    if (range.endsWith("+")) {
                        if (sale >= Integer.parseInt(range.substring(0, range.length() - 1).trim())) return true;
                    } else {
                        String[] parts = range.split(" to ");
                        int lo = Integer.parseInt(parts[0].trim());
                        int hi = Integer.parseInt(parts[1].trim());
                        if (sale >= lo && sale <= hi) return true;
                    }
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                    // rango malformado se ignora (validación de entrada en trust boundary)
                }
            }
            return false;
        }
    }
}
