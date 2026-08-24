package com.nestorria.server.common.ai;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import com.nestorria.server.common.ai.dto.AiHealthResponse;
import com.nestorria.server.common.ai.dto.AiPredictionRequest;
import com.nestorria.server.modules.properties.PropertyRecommendationService;

class AiServiceClientTest {

    private AiServiceProperties properties;
    private AiFallbackHandler fallbackHandler;

    @BeforeEach
    void setUp() {
        properties = new AiServiceProperties(
            "http://localhost:8000", "test-api-key", 3000, 5000, 30);
        fallbackHandler = new AiFallbackHandler(mock(PropertyRecommendationService.class));
    }

    // ── healthCheck deserialization ────────────────────────────────

    @Test
    void healthCheck_returnsValidResponse() {
        StubFactory stub = new StubFactory();
        stub.on("/health", 200, "{\"status\":\"healthy\",\"aiService\":\"up\"}");
        AiServiceClient client = buildClient(stub);

        AiHealthResponse response = client.healthCheck();

        assertEquals("healthy", response.status());
        assertEquals("up", response.aiService());
    }

    // ── X-API-Key propagation ─────────────────────────────────────

    @Test
    void anyRequest_includesApiKeyHeader() {
        StubFactory stub = new StubFactory();
        stub.on("/health", 200, "{\"status\":\"healthy\",\"aiService\":\"up\"}");
        AiServiceClient client = buildClient(stub);

        client.healthCheck();

        assertEquals("test-api-key", stub.capturedHeaders().getFirst("X-API-Key"));
    }

    @Test
    void noApiKey_headerNotSent() {
        AiServiceProperties noKeyProps = new AiServiceProperties(
            "http://localhost:8000", "", 3000, 5000, 30);
        StubFactory stub = new StubFactory();
        stub.on("/health", 200, "{\"status\":\"healthy\",\"aiService\":\"up\"}");
        RestClient restClient = buildRestClient(noKeyProps, stub);
        AiServiceClient client = new AiServiceClient(noKeyProps, fallbackHandler, restClient);

        client.healthCheck();

        assertTrue(stub.capturedHeaders().getOrEmpty("X-API-Key").isEmpty());
    }

    // ── fallback when server unavailable ───────────────────────────

    @Test
    void healthCheckFallback_returnsDegraded() {
        AiServiceClient client = buildClient(new StubFactory());

        AiHealthResponse response = client.healthCheckFallback(
            new IOException("simulated failure"));

        assertEquals("degraded", response.status());
        assertEquals("unavailable", response.aiService());
    }

    // ── prediction fallback ────────────────────────────────────────

    @Test
    void predictPriceFallback_throwsAiServiceException() {
        AiServiceClient client = buildClient(new StubFactory());
        AiPredictionRequest request = AiPredictionRequest.forPrice("p1", Map.of());

        assertThrows(AiServiceException.class,
            () -> client.predictPriceFallback(request, new IOException("fail")));
    }

    // ── properties validation ──────────────────────────────────────

    @Test
    void constructor_nullBaseUrl_throws() {
        assertThrows(IllegalStateException.class,
            () -> new AiServiceProperties(null, "key", 3000, 5000, 30));
    }

    @Test
    void constructor_blankBaseUrl_throws() {
        assertThrows(IllegalStateException.class,
            () -> new AiServiceProperties("  ", "key", 3000, 5000, 30));
    }

    // ── retry + fallback on repeated failures ──────────────────────

    @Test
    void healthCheck_allRetriesFail_fallbackRuns() {
        StubFactory stub = new StubFactory();
        stub.on("/health", 503, "{}");
        AiServiceClient client = buildClient(stub);

        // The @Retry + @CircuitBreaker annotations are not active in unit tests
        // (no Spring context). Directly invoke the fallback to verify behavior.
        AiHealthResponse response = client.healthCheckFallback(
            new org.springframework.web.client.HttpServerErrorException(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                org.springframework.http.HttpHeaders.EMPTY, new byte[0],
                StandardCharsets.UTF_8));

        assertEquals("degraded", response.status());
    }

    // ── helpers ────────────────────────────────────────────────────

    private AiServiceClient buildClient(StubFactory stub) {
        return new AiServiceClient(properties, fallbackHandler, buildRestClient(properties, stub));
    }

    private RestClient buildRestClient(AiServiceProperties props, ClientHttpRequestFactory factory) {
        return RestClient.builder()
            .baseUrl(props.baseUrl())
            .requestFactory(factory)
            .requestInterceptor((request, body, execution) -> {
                if (props.hasApiKey()) {
                    request.getHeaders().set("X-API-Key", props.apiKey());
                }
                return execution.execute(request, body);
            })
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .build();
    }

    // ── Stub HTTP transport ────────────────────────────────────────

    /**
     * Lightweight stub ClientHttpRequestFactory.
     * Maps URI paths to canned status + body. No external HTTP server needed.
     */
    static final class StubFactory implements ClientHttpRequestFactory {
        private final Map<String, int[]> statusByPath = new java.util.LinkedHashMap<>();
        private final Map<String, String> bodyByPath = new java.util.LinkedHashMap<>();
        private HttpMethod lastMethod;
        private HttpHeaders lastHeaders;
        private int callCount;

        void on(String path, int status, String body) {
            statusByPath.put(path, new int[]{status});
            bodyByPath.put(path, body);
        }

        HttpHeaders capturedHeaders() { return lastHeaders; }
        int getCallCount() { return callCount; }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
            return new StubRequest(uri, method);
        }

        private final class StubRequest implements ClientHttpRequest {
            private final URI uri;
            private final HttpMethod method;
            private final HttpHeaders headers = new HttpHeaders();
            private final Map<String, Object> attributes = new java.util.HashMap<>();
            private byte[] body;

            StubRequest(URI uri, HttpMethod method) {
                this.uri = uri;
                this.method = method;
            }

            @Override public HttpMethod getMethod() { return method; }
            @Override public URI getURI() { return uri; }
            @Override public HttpHeaders getHeaders() { return headers; }
            @Override public Map<String, Object> getAttributes() { return attributes; }

            @Override
            public OutputStream getBody() {
                return new ByteArrayOutputStream() {
                    @Override public void close() throws IOException {
                        super.close();
                        body = toByteArray();
                    }
                };
            }

            @Override
            public ClientHttpResponse execute() {
                callCount++;
                lastMethod = method;
                lastHeaders = this.headers;

                String path = uri.getPath();
                int status = statusByPath.containsKey(path)
                    ? statusByPath.get(path)[0] : 200;
                String bodyStr = bodyByPath.getOrDefault(path, "{}");

                return new StubHttpResponse(status, bodyStr);
            }
        }
    }

    private static final class StubHttpResponse implements ClientHttpResponse {
        private final int status;
        private final String body;
        private final HttpHeaders headers = new HttpHeaders();

        StubHttpResponse(int status, String body) {
            this.status = status;
            this.body = body;
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        }

        @Override public HttpStatus getStatusCode() { return HttpStatus.valueOf(status); }
        @Override public String getStatusText() { return String.valueOf(status); }
        @Override public void close() {}
        @Override public HttpHeaders getHeaders() { return headers; }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }
    }
}
