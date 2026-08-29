package com.nestorria.server.common.ai;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.common.ai.dto.ToolBookingStatsResponse;
import com.nestorria.server.common.ai.dto.ToolPropertyAvgPriceResponse;
import com.nestorria.server.common.ai.dto.ToolPropertyCountResponse;
import com.nestorria.server.common.ai.dto.ToolPropertySearchResponse;
import com.nestorria.server.common.ai.dto.ToolReviewAverageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Endpoints internos para herramientas del LLM.
 * Protegidos por API key (via ToolEndpointAuthFilter), NO por JWT.
 * Solo lectura — no permiten escritura.
 * Rate limiting: reutiliza el Bucket4j existente para /api/ai (30/min).
 */
@RestController
@RequestMapping("/api/ai/tools")
@Validated
@Tag(name = "AI Tools", description = "Endpoints internos para herramientas del LLM (solo lectura)")
public class AiToolController {

    private final AiToolService toolService;

    public AiToolController(AiToolService toolService) {
        this.toolService = toolService;
    }

    @Operation(summary = "Contar propiedades con filtros opcionales")
    @GetMapping("/properties/count")
    public ToolPropertyCountResponse getPropertyCount(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String propertyType) {
        return toolService.getPropertyCount(city, propertyType);
    }

    @Operation(summary = "Precio promedio de propiedades con filtros opcionales")
    @GetMapping("/properties/avg-price")
    public ToolPropertyAvgPriceResponse getAveragePrice(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String propertyType) {
        return toolService.getAveragePrice(city, propertyType);
    }

    @Operation(summary = "Buscar propiedades con filtros")
    @GetMapping("/properties/search")
    public ToolPropertySearchResponse searchProperties(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String propertyType,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice) {
        return toolService.searchProperties(city, propertyType, minPrice, maxPrice);
    }

    @Operation(summary = "Estadísticas generales de bookings")
    @GetMapping("/bookings/stats")
    public ToolBookingStatsResponse getBookingStats() {
        return toolService.getBookingStats();
    }

    @Operation(summary = "Rating promedio de reseñas de una propiedad")
    @GetMapping("/reviews/average")
    public ToolReviewAverageResponse getReviewAverage(
            @RequestParam String propertyId) {
        return toolService.getReviewAverage(propertyId);
    }
}
