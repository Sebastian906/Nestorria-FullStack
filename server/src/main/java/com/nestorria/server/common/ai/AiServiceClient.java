package com.nestorria.server.common.ai;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.nestorria.server.common.ai.dto.AiChatRequest;
import com.nestorria.server.common.ai.dto.AiChatResponse;
import com.nestorria.server.common.ai.dto.AiHealthResponse;
import com.nestorria.server.common.ai.dto.AiPredictionRequest;
import com.nestorria.server.common.ai.dto.AiPredictionResponse;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

/**
 * Cliente HTTP para comunicarse con ai-service.
 * Resiliencia:
 * - Circuit Breaker: abre después de 5 fallos en ventana de 10 requests
 * - Retry: 3 intentos con exponential backoff (500ms, 1s, 2s)
 * - Timeout: connect 3s, read 5s (operaciones síncronas)
 * - Fallback: delega a AiFallbackHandler (solo en @Retry, no en @CircuitBreaker)
 *
 * Streaming:
 * - Usa un RestClient separado con read timeout extendido (30s)
 * - No aplica @CircuitBreaker/@Retry (el stream se lee de forma lazy)
 *
 * Aspect ordering: @Retry (outer) → @CircuitBreaker (inner).
 * - Transport failures → retried by @Retry → fallback on exhaustion
 * - Open circuit → CallNotPermittedException → ignored by retry → fallback
 * @see AiServiceProperties
 * @see AiFallbackHandler
 */
@Service
@Slf4j
public class AiServiceClient {

    private final RestClient restClient;
    private final RestClient streamingRestClient;
    private final AiServiceProperties properties;
    private final AiFallbackHandler fallbackHandler;

    @Autowired
    public AiServiceClient(
            AiServiceProperties properties,
            AiFallbackHandler fallbackHandler) {
        this.properties = properties;
        this.fallbackHandler = fallbackHandler;
        this.restClient = buildRestClient(properties, properties.readTimeout());
        this.streamingRestClient = buildRestClient(properties, properties.chatStreamReadTimeout());
    }

    // Package-private: allows tests to inject a RestClient with a mock transport
    AiServiceClient(
            AiServiceProperties properties,
            AiFallbackHandler fallbackHandler,
            RestClient restClient) {
        this.properties = properties;
        this.fallbackHandler = fallbackHandler;
        this.restClient = restClient;
        this.streamingRestClient = restClient; // tests don't need separate streaming client
    }

    private static RestClient buildRestClient(AiServiceProperties properties, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeout()));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        ClientHttpRequestInterceptor apiKeyInterceptor = (request, body, execution) -> {
            if (properties.hasApiKey()) {
                request.getHeaders().set("X-API-Key", properties.apiKey());
            }
            return execution.execute(request, body);
        };

        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(factory)
            .requestInterceptor(apiKeyInterceptor)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    // Health Check
    @CircuitBreaker(name = "ai-service")
    @Retry(name = "ai-service", fallbackMethod = "healthCheckFallback")
    public AiHealthResponse healthCheck() {
        log.debug("ai-service health check");
        return restClient.get()
            .uri("/health")
            .retrieve()
            .body(AiHealthResponse.class);
    }

    AiHealthResponse healthCheckFallback(Throwable t) {
        log.warn("ai-service health check failed: {}", t.getMessage());
        return fallbackHandler.healthFallback();
    }

    // Predict Price
    @CircuitBreaker(name = "ai-service")
    @Retry(name = "ai-service", fallbackMethod = "predictPriceFallback")
    public AiPredictionResponse predictPrice(AiPredictionRequest request) {
        log.debug("ai-service predict price: propertyId={}", request.propertyId());
        return restClient.post()
            .uri("/predict/price")
            .body(request)
            .retrieve()
            .body(AiPredictionResponse.class);
    }

    AiPredictionResponse predictPriceFallback(AiPredictionRequest request, Throwable t) {
        log.warn("ai-service predict price fallback: propertyId={}, error={}",
            request.propertyId(), t.getMessage());
        return fallbackHandler.pricePredictionFallback(request);
    }

    // Predict Cancellation
    @CircuitBreaker(name = "ai-service")
    @Retry(name = "ai-service", fallbackMethod = "predictCancellationFallback")
    public AiPredictionResponse predictCancellation(AiPredictionRequest request) {
        log.debug("ai-service predict cancellation: bookingId={}", request.bookingId());
        return restClient.post()
            .uri("/predict/cancellation")
            .body(request)
            .retrieve()
            .body(AiPredictionResponse.class);
    }

    AiPredictionResponse predictCancellationFallback(AiPredictionRequest request, Throwable t) {
        log.warn("ai-service predict cancellation fallback: bookingId={}, error={}",
            request.bookingId(), t.getMessage());
        return fallbackHandler.cancellationPredictionFallback(request);
    }

    // Chat
    @CircuitBreaker(name = "ai-service")
    @Retry(name = "ai-service", fallbackMethod = "chatFallback")
    public AiChatResponse chat(AiChatRequest request) {
        log.debug("ai-service chat: userId={}", request.userId());
        return restClient.post()
            .uri("/chat")
            .body(request)
            .retrieve()
            .body(AiChatResponse.class);
    }

    AiChatResponse chatFallback(AiChatRequest request, Throwable t) {
        log.warn("ai-service chat fallback: userId={}, error={}",
                request.userId(), t.getMessage());
        return fallbackHandler.chatFallback(request);
    }

    // Chat Streaming — blocking InputStream, uses dedicated streaming RestClient
    /**
     * Consume SSE streaming de ai-service.
     * Usa un RestClient dedicado con read timeout extendido (30s)
     * para no bloquear las operaciones síncronas (5s read timeout).
     *
     * No se aplica @CircuitBreaker/@Retry porque RestClient es síncrono
     * y el stream se lee de forma lazy. El manejo de errores se hace
     * en el caller (AiChatStreamingService).
     *
     * @param request chat request con message, userId, conversationId
     * @return InputStream con el contenido SSE (text/event-stream)
     */
    public InputStream streamChat(AiChatRequest request) {
        log.debug("ai-service stream chat: userId={}", request.userId());
        return streamingRestClient.post()
            .uri("/rag/chat")
            .header("X-User-ID", request.userId())
            .body(request)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .body(InputStream.class);
    }

    // Recommendations
    @CircuitBreaker(name = "ai-service")
    @Retry(name = "ai-service", fallbackMethod = "recommendationsFallback")
    public List<PropertySummaryResponse> getAiRecommendations(String userId, int limit) {
        log.debug("ai-service recommendations: userId={}, limit={}", userId, limit);
        return restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/recommendations")
                .queryParam("user_id", userId)
                .queryParam("limit", limit)
                .build())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    List<PropertySummaryResponse> recommendationsFallback(String userId, int limit, Throwable t) {
        log.warn("ai-service recommendations fallback: userId={}, error={}", userId, t.getMessage());
        return fallbackHandler.recommendationsFallback(userId, limit);
    }
}
