package com.nestorria.server.config;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.nestorria.server.common.config.AppProperties;
import com.nestorria.server.common.config.AppProperties.RateLimitProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @Mock
    private FilterChain filterChain;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private static final String TEST_USER_ID = "user-rate-limit-test";

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties(
            true, 100, 10, 5, 10, 60, 30
        );
        AppProperties appProps = new AppProperties(
            null, "$", null, null, props
        );
        filter = new RateLimitFilter(appProps);
    }

    private void authenticateAsUser(String userId) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(userId)
            .claim("iss", "https://test.issuer")
            .claim("exp", Instant.now().plusSeconds(3600))
            .build();
        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);

        when(securityContext.getAuthentication()).thenReturn(jwtAuth);
        SecurityContextHolder.setContext(securityContext);
    }

    private void authenticateAnon() {
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);
    }

    // === Exclusion tests ===

    @Test
    void healthEndpoint_IsExcluded() throws ServletException, IOException {
        authenticateAnon();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertFalse(response.containsHeader("X-Rate-Limit-Remaining"));
    }

    @Test
    void stripeWebhook_IsExcluded() throws ServletException, IOException {
        authenticateAnon();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments/stripe/webhook");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertFalse(response.containsHeader("Retry-After"));
    }

    // === Authenticated rate limiting ===

    @Test
    void authenticatedRequest_AddsRemainingHeader() throws ServletException, IOException {
        authenticateAsUser(TEST_USER_ID);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
        assertTrue(response.containsHeader("X-Rate-Limit-Remaining"));
        long remaining = Long.parseLong(response.getHeader("X-Rate-Limit-Remaining"));
        assertTrue(remaining <= 100 && remaining >= 0);
    }

    @Test
    void writeEndpoint_UsesWriteLimit() throws ServletException, IOException {
        authenticateAsUser(TEST_USER_ID);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bookings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Consume 10 tokens (write limit)
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(request, res, filterChain);
        }

        // 11th should be rejected
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilterInternal(request, response2, filterChain);

        assertEquals(429, response2.getStatus());
        assertTrue(response2.containsHeader("Retry-After"));
    }

    @Test
    void reviewEndpoint_UsesReviewLimit() throws ServletException, IOException {
        authenticateAsUser(TEST_USER_ID);
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
            "/api/properties/p1/reviews");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Consume 5 tokens (review limit)
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(request, res, filterChain);
        }

        // 6th should be rejected
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        filter.doFilterInternal(request, response2, filterChain);

        assertEquals(429, response2.getStatus());
    }

    // === Per-user isolation ===

    @Test
    void differentUsers_HaveSeparateBuckets() throws ServletException, IOException {
        // User 1 exhausts their limit
        authenticateAsUser("user-1");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bookings");
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(request, res, filterChain);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(request, blocked, filterChain);
        assertEquals(429, blocked.getStatus());

        // User 2 still has full limit
        authenticateAsUser("user-2");
        MockHttpServletResponse allowed = new MockHttpServletResponse();
        filter.doFilterInternal(request, allowed, filterChain);
        assertEquals(200, allowed.getStatus());
    }

    // === IP-based limiting for anonymous ===

    @Test
    void anonymousUser_IsLimitedByIp() throws ServletException, IOException {
        authenticateAnon();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/properties/me");
        request.setRemoteAddr("192.168.1.1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertTrue(response.containsHeader("X-Rate-Limit-Remaining"));
    }

    // === Disabled rate limiting ===

    @Test
    void whenDisabled_AllRequestsAllowed() throws ServletException, IOException {
        RateLimitProperties props = new RateLimitProperties(
            false, 100, 10, 5, 10, 60, 30
        );
        AppProperties appProps = new AppProperties(
            null, "$", null, null, props
        );
        RateLimitFilter disabledFilter = new RateLimitFilter(appProps);

        authenticateAsUser(TEST_USER_ID);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bookings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        disabledFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertFalse(response.containsHeader("Retry-After"));
    }
}
