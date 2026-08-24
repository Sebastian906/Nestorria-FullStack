package com.nestorria.server.common.ai;

import java.util.List;

import org.springframework.stereotype.Component;

import com.nestorria.server.common.ai.dto.AiChatRequest;
import com.nestorria.server.common.ai.dto.AiChatResponse;
import com.nestorria.server.common.ai.dto.AiHealthResponse;
import com.nestorria.server.common.ai.dto.AiPredictionRequest;
import com.nestorria.server.common.ai.dto.AiPredictionResponse;
import com.nestorria.server.modules.properties.PropertyRecommendationService;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Fallback handler para ai-service.
 * Estrategia por operación:
 * - Health: retorna "degraded" (no intenta alternativa)
 * - Recommendations: delega a PropertyRecommendationService (algoritmo existente)
 * - Predictions: retorna error controlado (no hay heurística existente)
 * - Chat: retorna mensaje de indisponibilidad
 */
@Component
@Slf4j
public class AiFallbackHandler {

    private final PropertyRecommendationService recommendationService;

    public AiFallbackHandler(PropertyRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    public AiHealthResponse healthFallback() {
        return new AiHealthResponse("degraded", "unavailable");
    }

    public AiPredictionResponse pricePredictionFallback(AiPredictionRequest request) {
        // No hay heurística existente para predicción de precios.
        // Retornar error controlado en lugar de datos falsos.
        throw new AiServiceException(
            "Servicio de predicción de precios no disponible. "
            + "Intente nuevamente más tarde.");
    }

    public AiPredictionResponse cancellationPredictionFallback(AiPredictionRequest request) {
        // No hay heurística existente para predicción de cancelación.
        throw new AiServiceException(
            "Servicio de predicción de cancelación no disponible. "
            + "Intente nuevamente más tarde.");
    }

    public AiChatResponse chatFallback(AiChatRequest request) {
        return new AiChatResponse(
            "El servicio de IA no está disponible temporalmente. "
            + "Por favor, intente nuevamente en unos minutos.",
            List.of(),
            request.conversationId()
        );
    }

    public List<PropertySummaryResponse> recommendationsFallback(String userId, int limit) {
        log.info("recommendations fallback: delegando a PropertyRecommendationService");
        return recommendationService.getRecommendations(userId, limit);
    }
}
