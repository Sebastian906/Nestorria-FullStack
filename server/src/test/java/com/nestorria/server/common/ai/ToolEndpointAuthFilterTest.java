package com.nestorria.server.common.ai;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class ToolEndpointAuthFilterTest {

    private ToolEndpointAuthFilter filter;
    private AiServiceProperties properties;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        properties = new AiServiceProperties(
            "http://localhost:4000", "test-api-key-123", 3000, 5000, 30);
        filter = new ToolEndpointAuthFilter(properties);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonToolEndpoint_skipsFilter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ai/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void toolEndpoint_noApiKey_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ai/tools/properties/count");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void toolEndpoint_blankApiKey_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ai/tools/properties/count");
        request.addHeader("X-API-Key", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void toolEndpoint_invalidApiKey_returns403() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ai/tools/bookings/stats");
        request.addHeader("X-API-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void toolEndpoint_validApiKey_setsAuthentication() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ai/tools/reviews/average");
        request.addHeader("X-API-Key", "test-api-key-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("ai-service", auth.getPrincipal());
        assertNotNull(auth.getAuthorities());
        assertTrue(auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_AI_SERVICE")));
    }

    @Test
    void toolEndpoint_noApiKeyConfigured_returns403() throws ServletException, IOException {
        AiServiceProperties noKeyProps = new AiServiceProperties(
            "http://localhost:4000", "", 3000, 5000, 30);
        ToolEndpointAuthFilter filterNoKey = new ToolEndpointAuthFilter(noKeyProps);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ai/tools/properties/count");
        request.addHeader("X-API-Key", "some-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterNoKey.doFilterInternal(request, response, filterChain);

        // Key is present but server has none configured → 403 (key invalid), not 401 (no key)
        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void toolEndpoint_nestedPath_alsoProtected() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ai/tools/properties/search");
        request.addHeader("X-API-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
    }

    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
