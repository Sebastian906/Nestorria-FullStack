package com.nestorria.server.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.common.ai.AiServiceClient;
import com.nestorria.server.common.ai.dto.AiChatRequest;
import com.nestorria.server.common.ai.dto.AiChatResponse;
import com.nestorria.server.common.ai.dto.AiHealthResponse;
import com.nestorria.server.common.ai.dto.AiPredictionRequest;
import com.nestorria.server.common.ai.dto.AiPredictionResponse;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai")
@Validated
@Tag(name = "AI", description = "Endpoints internos para integración con ai-service")
public class AiController {

    private final AiServiceClient aiServiceClient;

    public AiController(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @Operation(summary = "Health check de ai-service")
    @GetMapping("/health")
    public AiHealthResponse health() {
        return aiServiceClient.healthCheck();
    }

    @Operation(summary = "Predicción de precio de propiedad")
    @PostMapping("/predict/price")
    public AiPredictionResponse predictPrice(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AiPredictionRequest request) {
        return aiServiceClient.predictPrice(request);
    }

    @Operation(summary = "Predicción de cancelación de reserva")
    @PostMapping("/predict/cancellation")
    public AiPredictionResponse predictCancellation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AiPredictionRequest request) {
        return aiServiceClient.predictCancellation(request);
    }

    @Operation(summary = "Chat con IA")
    @PostMapping("/chat")
    public AiChatResponse chat(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AiChatRequest request) {
        // Inyectar userId del JWT si no viene en el request
        AiChatRequest enriched = new AiChatRequest(
            request.message(),
            jwt.getSubject(),
            request.conversationId()
        );
        return aiServiceClient.chat(enriched);
    }

    @Operation(summary = "Recomendaciones de propiedades vía IA")
    @GetMapping("/recommendations")
    public List<PropertySummaryResponse> recommendations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "10") int limit) {
        return aiServiceClient.getAiRecommendations(jwt.getSubject(), Math.min(limit, 20));
    }
}
