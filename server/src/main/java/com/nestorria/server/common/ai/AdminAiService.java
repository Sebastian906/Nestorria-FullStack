package com.nestorria.server.common.ai;

import java.time.Duration;
import java.util.List;

import com.nestorria.server.common.ai.dto.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Servicio admin para endpoints de gestión de AI.
 * Usa su propio RestClient (no AiServiceClient) porque:
 * - Endpoints admin usan auth JWT (Clerk), no X-API-Key
 * - Endpoints admin están bajo /ai/admin/* en ai-service
 * - Separación de responsabilidades: inferencia vs administración
 */
@Service
public class AdminAiService {

    private static final Logger log = LoggerFactory.getLogger(AdminAiService.class);

    private final RestClient restClient;

    public AdminAiService(AiServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeout()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeout()));

        this.restClient = RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(factory)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    public AdminModelsResponse getModels() {
        try {
            return restClient.get()
                .uri("/ai/admin/models")
                .retrieve()
                .body(AdminModelsResponse.class);
        } catch (Exception e) {
            log.error("Failed to fetch models from ai-service", e);
            throw new AiServiceException("Failed to fetch models", e);
        }
    }

    public TrainingResponse triggerTraining(String modelName) {
        log.info("Triggering training for model: {}", modelName);
        try {
            return restClient.post()
                .uri("/ai/admin/models/{modelName}/train", modelName)
                .retrieve()
                .body(TrainingResponse.class);
        } catch (Exception e) {
            log.error("Failed to trigger training for model: {}", modelName, e);
            throw new AiServiceException("Failed to trigger training", e);
        }
    }

    public AdminRagDocumentsResponse getDocuments() {
        try {
            return restClient.get()
                .uri("/ai/admin/rag/documents")
                .retrieve()
                .body(AdminRagDocumentsResponse.class);
        } catch (Exception e) {
            log.error("Failed to fetch RAG documents", e);
            throw new AiServiceException("Failed to fetch RAG documents", e);
        }
    }

    public void deleteDocument(String documentId) {
        try {
            restClient.delete()
                .uri("/ai/admin/rag/documents/{documentId}", documentId)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to delete document: {}", documentId, e);
            throw new AiServiceException("Failed to delete document", e);
        }
    }

    public AdminChatMetricsResponse getChatMetrics() {
        try {
            return restClient.get()
                .uri("/ai/admin/chat/metrics")
                .retrieve()
                .body(AdminChatMetricsResponse.class);
        } catch (Exception e) {
            log.error("Failed to fetch chat metrics", e);
            throw new AiServiceException("Failed to fetch chat metrics", e);
        }
    }

    public AdminAiStatusResponse getStatus() {
        try {
            return restClient.get()
                .uri("/ai/admin/status")
                .retrieve()
                .body(AdminAiStatusResponse.class);
        } catch (Exception e) {
            log.error("Failed to fetch AI status", e);
            return AdminAiStatusResponse.degraded();
        }
    }

    public ModelVersionsResponse getModelVersions(String modelName) {
        try {
            return restClient.get()
                .uri("/ai/admin/models/{modelName}/versions", modelName)
                .retrieve()
                .body(ModelVersionsResponse.class);
        } catch (Exception e) {
            log.error("Failed to fetch versions for model: {}", modelName, e);
            throw new AiServiceException("Failed to fetch model versions", e);
        }
    }

    public VersionInfoResponse getActiveVersion(String modelName) {
        try {
            return restClient.get()
                .uri("/ai/admin/models/{modelName}/active", modelName)
                .retrieve()
                .body(VersionInfoResponse.class);
        } catch (Exception e) {
            log.error("Failed to fetch active version for model: {}", modelName, e);
            throw new AiServiceException("Failed to fetch active version", e);
        }
    }

    public PromoteRollbackResponse promoteModel(String modelName, String version) {
        try {
            return restClient.post()
                .uri("/ai/admin/models/{modelName}/promote/{version}", modelName, version)
                .retrieve()
                .body(PromoteRollbackResponse.class);
        } catch (Exception e) {
            log.error("Failed to promote model {} to version {}", modelName, version, e);
            throw new AiServiceException("Failed to promote model", e);
        }
    }

    public PromoteRollbackResponse rollbackModel(String modelName, String version) {
        try {
            return restClient.post()
                .uri("/ai/admin/models/{modelName}/rollback/{version}", modelName, version)
                .retrieve()
                .body(PromoteRollbackResponse.class);
        } catch (Exception e) {
            log.error("Failed to rollback model {} to version {}", modelName, version, e);
            throw new AiServiceException("Failed to rollback model", e);
        }
    }

    public CompareVersionsResponse compareVersions(String modelName, String v1, String v2) {
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/ai/admin/models/{modelName}/compare")
                    .queryParam("v1", v1)
                    .queryParam("v2", v2)
                    .build(modelName))
                .retrieve()
                .body(CompareVersionsResponse.class);
        } catch (Exception e) {
            log.error("Failed to compare versions {} and {} for model {}", v1, v2, modelName, e);
            throw new AiServiceException("Failed to compare versions", e);
        }
    }
}
