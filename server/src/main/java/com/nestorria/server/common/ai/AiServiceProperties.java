package com.nestorria.server.common.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-service")
public record AiServiceProperties(
    String baseUrl,
    String apiKey,
    int connectTimeout,
    int readTimeout,
    int chatStreamReadTimeout,
    int maxConcurrentStreams,
    int aiPerMinute
) {
    public AiServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.ai-service.base-url no puede estar vacío");
        }
    }

    // API key para service-to-service auth. Puede ser null/vacío en desarrollo.
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
