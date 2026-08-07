package com.nestorria.server.config;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nestorria.server.common.config.AppProperties;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final AppProperties.RateLimitProperties rateLimitProps;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties appProperties) {
        this.rateLimitProps = appProperties.rateLimit();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!rateLimitProps.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();

        // Exclude health check and Stripe webhook (validated by Stripe signature)
        if (isExcluded(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveKey(request);
        int limit = resolveLimit(uri);
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(limit));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining",
                String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write(
                "{\"timestamp\":\"" + java.time.Instant.now()
                + "\",\"message\":\"Demasiadas solicitudes. Intenta de nuevo en "
                + retryAfterSeconds + " segundos.\"}");
        }
    }

    private boolean isExcluded(String uri) {
        return uri.equals("/api/health/")
            || uri.equals("/api/health")
            || uri.startsWith("/api/payments/stripe/webhook");
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return "user:" + jwt.getSubject();
        }

        // Unauthenticated — use IP
        return "ip:" + resolveClientIp(request);
    }

    /**
     * Resolve client IP. If behind a reverse proxy in the future,
     * add server.forward-headers-strategy=native to application.properties.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // First IP in the chain is the original client
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private int resolveLimit(String uri) {
        if (uri.startsWith("/api/bookings")) return rateLimitProps.writePerMinute();
        if (uri.contains("/reviews")) return rateLimitProps.reviewPerMinute();
        if (uri.startsWith("/api/invoices") || uri.startsWith("/api/payments/invoices")) {
            return rateLimitProps.writePerMinute();
        }
        if (uri.startsWith("/api/contracts")) return rateLimitProps.writePerMinute();
        if (uri.startsWith("/api/agencies")) return rateLimitProps.reviewPerMinute();
        if (uri.equals("/api/properties/me")
            || uri.startsWith("/api/properties/nearby")
            || uri.startsWith("/api/properties/*/reviews")) {
            return rateLimitProps.publicReadPerMinute();
        }
        if (uri.contains("/search") || uri.contains("/nearby")) {
            return rateLimitProps.searchPerMinute();
        }
        // Default: read operations for authenticated users
        return rateLimitProps.readPerMinute();
    }

    private Bucket createBucket(int requestsPerMinute) {
        return Bucket.builder()
            .addLimit(limit -> limit
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1)))
            .build();
    }
}
