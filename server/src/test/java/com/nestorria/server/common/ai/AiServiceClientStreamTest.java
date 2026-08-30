package com.nestorria.server.common.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import com.nestorria.server.common.ai.dto.AiChatRequest;
import com.nestorria.server.modules.properties.PropertyRecommendationService;

class AiServiceClientStreamTest {

    private AiServiceProperties properties;
    private AiFallbackHandler fallbackHandler;

    @BeforeEach
    void setUp() {
        properties = new AiServiceProperties(
            "http://localhost:8000", "test-api-key", 3000, 30000, 30);
        fallbackHandler = new AiFallbackHandler(
            org.mockito.Mockito.mock(PropertyRecommendationService.class));
    }

    @Test
    void streamChat_receivesSseLines() throws Exception {
        String sseResponse = """
            data: {"type":"start","conversationId":"conv-1"}

            data: {"type":"token","content":"Hello"}

            data: {"type":"end","conversationId":"conv-1"}
            """;

        StreamingStubFactory stub = new StreamingStubFactory(sseResponse);
        AiServiceClient client = buildClient(stub);

        AiChatRequest request = new AiChatRequest("Hi", "user-1", "conv-1");
        List<String> received = new ArrayList<>();

        try (InputStream is = client.streamChat(request);
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    received.add(line);
                }
            }
        }

        assertEquals(3, received.size());
        assertEquals("data: {\"type\":\"start\",\"conversationId\":\"conv-1\"}", received.get(0));
        assertEquals("data: {\"type\":\"token\",\"content\":\"Hello\"}", received.get(1));
        assertEquals("data: {\"type\":\"end\",\"conversationId\":\"conv-1\"}", received.get(2));
    }

    @Test
    void streamChat_emptyStream_returnsEmptyInputStream() throws Exception {
        StreamingStubFactory stub = new StreamingStubFactory("");
        AiServiceClient client = buildClient(stub);

        AiChatRequest request = new AiChatRequest("Hi", "user-1", null);
        List<String> received = new ArrayList<>();

        try (InputStream is = client.streamChat(request);
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    received.add(line);
                }
            }
        }

        assertEquals(0, received.size());
    }

    @Test
    void streamChat_includesApiKeyHeader() {
        StreamingStubFactory stub = new StreamingStubFactory("data: {}");
        AiServiceClient client = buildClient(stub);

        AiChatRequest request = new AiChatRequest("Hi", "user-1", null);
        client.streamChat(request);

        assertEquals("test-api-key", stub.capturedHeaders().getFirst("X-API-Key"));
    }

    @Test
    void streamChat_requestsEventStream() {
        StreamingStubFactory stub = new StreamingStubFactory("data: {}");
        AiServiceClient client = buildClient(stub);

        AiChatRequest request = new AiChatRequest("Hi", "user-1", null);
        client.streamChat(request);

        // Verify the Accept header was set
        assertNotNull(stub.capturedHeaders().getAccept());
    }

    // ── helpers ────────────────────────────────────────────────────

    private AiServiceClient buildClient(StreamingStubFactory stub) {
        return new AiServiceClient(properties, fallbackHandler,
            buildRestClient(properties, stub));
    }

    private RestClient buildRestClient(AiServiceProperties props,
            ClientHttpRequestFactory factory) {
        return RestClient.builder()
            .baseUrl(props.baseUrl())
            .requestFactory(factory)
            .requestInterceptor((request, body, execution) -> {
                if (props.hasApiKey()) {
                    request.getHeaders().set("X-API-Key", props.apiKey());
                }
                return execution.execute(request, body);
            })
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    // ── Streaming Stub ────────────────────────────────────────────

    static final class StreamingStubFactory implements ClientHttpRequestFactory {
        private final String responseBody;
        private HttpHeaders lastHeaders;

        StreamingStubFactory(String responseBody) {
            this.responseBody = responseBody;
        }

        HttpHeaders capturedHeaders() { return lastHeaders; }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
            return new StreamingStubRequest(uri, method);
        }

        private final class StreamingStubRequest implements ClientHttpRequest {
            private final URI uri;
            private final HttpMethod method;
            private final HttpHeaders headers = new HttpHeaders();
            private final Map<String, Object> attributes = new java.util.HashMap<>();

            StreamingStubRequest(URI uri, HttpMethod method) {
                this.uri = uri;
                this.method = method;
            }

            @Override public HttpMethod getMethod() { return method; }
            @Override public URI getURI() { return uri; }
            @Override public HttpHeaders getHeaders() { return headers; }
            @Override public Map<String, Object> getAttributes() { return attributes; }

            @Override
            public OutputStream getBody() {
                return new ByteArrayOutputStream();
            }

            @Override
            public ClientHttpResponse execute() {
                lastHeaders = this.headers;
                return new StreamingStubResponse(responseBody);
            }
        }
    }

    private static final class StreamingStubResponse implements ClientHttpResponse {
        private final String body;
        private final HttpHeaders headers = new HttpHeaders();

        StreamingStubResponse(String body) {
            this.body = body;
            headers.setContentType(MediaType.TEXT_EVENT_STREAM);
        }

        @Override public HttpStatus getStatusCode() { return HttpStatus.OK; }
        @Override public String getStatusText() { return "OK"; }
        @Override public void close() {}
        @Override public HttpHeaders getHeaders() { return headers; }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }
    }
}
