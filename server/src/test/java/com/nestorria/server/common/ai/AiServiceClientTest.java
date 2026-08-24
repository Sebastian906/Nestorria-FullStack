package com.nestorria.server.common.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.springframework.web.client.RestClient;

class AiServiceClientTest {

    private AiServiceClient client;
    private AiServiceProperties properties;
    private AiFallbackHandler fallbackHandler;
    private RestClient.Builder restClientBuilder;

    // Mock HTTP server interceptor para simular respuestas
    // (WireMock o MockWebServer no están en el proyecto;
    //  usar RestClient.Builder con un interceptor mock)

    @BeforeEach
    void setUp() {
        properties = new AiServiceProperties(
            "http://localhost:8000", "test-api-key", 3000, 5000, 30);
        fallbackHandler = mock(AiFallbackHandler.class);
        restClientBuilder = RestClient.builder();
        client = new AiServiceClient(properties, fallbackHandler);
    }

    @Test
    void healthCheck_returnsValidResponse() {
        // Arrange: configurar mock interceptor para retornar 200 con JSON
        // Act: client.healthCheck()
        // Assert: verificar que el objeto AiHealthResponse tiene los campos correctos
    }

    @Test
    void anyRequest_includesApiKeyHeader() {
        // Arrange: configurar interceptor que verifique el header
        // Act: client.healthCheck()
        // Assert: verificar que X-API-Key fue enviado
    }

    @Test
    void healthCheck_serverUnavailable_fallback() {
        // Arrange: configurar mock para retornar 500
        // Act: client.healthCheck()
        // Assert: fallbackHandler.healthFallback() fue llamado
    }
}
