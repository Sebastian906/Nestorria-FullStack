package com.nestorria.server.modules.properties;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nestorria.server.common.exception.BadRequestException;
import com.nestorria.server.modules.favorite.FavoriteService;
import com.nestorria.server.modules.properties.PropertySortUtils.SortDirection;
import com.nestorria.server.modules.properties.PropertySortUtils.SortField;
import com.nestorria.server.modules.properties.dto.CreatePropertyRequest;
import com.nestorria.server.modules.properties.dto.NearbySearchRequest;
import com.nestorria.server.modules.properties.dto.PropertyResponse;
import com.nestorria.server.modules.properties.dto.PropertyStatsResponse;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;
import com.nestorria.server.modules.properties.dto.ToggleAvailabilityRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/properties")
@Tag(name = "Properties", description = "Gestión de propiedades inmobiliarias")
public class PropertyController {

    private final PropertyService propertyService;
    private final PropertySearchService propertySearchService;
    private final FavoriteService favoriteService;

    public PropertyController(
            PropertyService propertyService,
            PropertySearchService propertySearchService,
            FavoriteService favoriteService) {
        this.propertyService = propertyService;
        this.propertySearchService = propertySearchService;
        this.favoriteService = favoriteService;
    }

    // POST /api/properties — requiere autenticación y agencia registrada
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PropertyResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("data") CreatePropertyRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {

        PropertyResponse response = propertyService.create(jwt.getSubject(), request, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /api/properties/me — público
    @Operation(summary = "Obtener todas las propiedades disponibles")
    @GetMapping("/me")
    public List<PropertySummaryResponse> getAllAvailable(
            @Parameter(description = "Campo de ordenamiento: PRICE, DATE, AREA, RATING")
            @RequestParam(required = false) SortField sortBy,
            @Parameter(description = "Dirección del ordenamiento: ASC o DESC")
            @RequestParam(required = false) SortDirection direction
    ) {
        List<PropertySummaryResponse> properties = propertyService.getAllAvailable();

        if (sortBy != null) {
            SortDirection dir = direction != null ? direction : SortDirection.ASC;
            properties = properties.stream()
                .sorted(PropertySortUtils.getComparator(sortBy, dir))
                .toList();
        }

        return properties;
    }

    // GET /api/properties/stats — Estadísticas de propiedades (usa SearchUtils sobre datos cacheados)
    @GetMapping("/stats")
    public PropertyStatsResponse getStats(@AuthenticationPrincipal Jwt jwt) {
        return propertyService.getPropertyStats();
    }

    // GET /api/properties/owner — solo el dueño de una agencia
    @GetMapping("/owner")
    public List<PropertyResponse> getOwnerProperties(@AuthenticationPrincipal Jwt jwt) {
        return propertyService.getOwnerProperties(jwt.getSubject());
    }

    // PATCH /api/properties/{id}/availability — cambia semántica: POST→PATCH, body→path variable
    @PatchMapping("/{id}/availability")
    public ResponseEntity<Void> toggleAvailability(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {

        propertyService.toggleAvailability(jwt.getSubject(), new ToggleAvailabilityRequest(id));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Toggle favorito de una propiedad (requiere autenticación)")
    @ApiResponse(responseCode = "200", description = "Estado del favorito cambiado exitosamente")
    @PostMapping("/{propertyId}/favorite")
    public ResponseEntity<Map<String, Boolean>> toggleFavorite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String propertyId) {

        boolean favorited = favoriteService.toggleFavorite(jwt.getSubject(), propertyId);
        return ResponseEntity.ok(Map.of("favorited", favorited));
    }

    @Operation(summary = "Buscar propiedades cercanas por coordenadas (público)")
    @GetMapping("/nearby")
    public List<PropertySummaryResponse> findNearby(@Valid NearbySearchRequest request) {
        if (request.lat() == null || request.lng() == null) {
            throw new BadRequestException("Los parámetros lat y lng son obligatorios para la búsqueda cercana");
        }
        return propertySearchService.findNearby(request.lat(), request.lng(), request.radiusKm());
    }

    @Operation(summary = "Búsqueda combinada de propiedades con filtros (requiere autenticación)")
    @GetMapping("/search")
    public List<PropertySummaryResponse> search(
            @AuthenticationPrincipal Jwt jwt,
            @Valid NearbySearchRequest request) {
        return propertySearchService.findByFilters(
            request.city(), request.propertyType(),
            request.minPrice(), request.maxPrice(),
            request.lat(), request.lng(), request.radiusKm()
        );
    }
}