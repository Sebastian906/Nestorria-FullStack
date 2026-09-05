package com.nestorria.server.config;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nestorria.server.common.config.AppProperties;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final AppProperties.RateLimitProperties rateLimitProps;
    private final Counter rateLimitRejected;
    private final ConcurrentHashMap<String, TimedBucket> buckets = new ConcurrentHashMap<>();

    // Evict buckets inactive for 2x the refill interval (1 min refill → 2 min expiry)
    private static final long EXPIRY_NANOS = Duration.ofMinutes(2).toNanos();
    private static final int MAX_BUCKETS = 10_000;

    public RateLimitFilter(AppProperties appProperties, MeterRegistry meterRegistry) {
        this.rateLimitProps = appProperties.rateLimit();
        this.rateLimitRejected = Counter.builder("rate_limit_rejected_total")
                .description("Total rate limit rejections")
                .register(meterRegistry);
    }

    private static final class TimedBucket {
        final Bucket bucket;
        volatile long lastAccessNanos;

        TimedBucket(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccessNanos = System.nanoTime();
        }
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
        // Normalize: strip context path for matching
        String path = uri.substring(request.getContextPath().length());

        // Exclude health check and Stripe webhook (validated by Stripe signature)
        if (isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = resolveKey(request);
        int limit = resolveLimit(path);
        String bucketKey = key + ":" + limit;
        TimedBucket tb = buckets.computeIfAbsent(bucketKey, k -> new TimedBucket(createBucket(limit)));
        tb.lastAccessNanos = System.nanoTime();
        Bucket bucket = tb.bucket;

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining",
                String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            // Punto real de rechazo — única instrumentación
            rateLimitRejected.increment();
            long retryAfterSeconds =
                (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000;
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("X-Rate-Limit-Remaining", "0");
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
            || uri.equals("/actuator/health")
            || uri.startsWith("/actuator/health/")
            || uri.equals("/actuator/prometheus")
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
     * Resolve client IP. Returns getRemoteAddr() by default.
     * Parses X-Forwarded-For only when the immediate peer is a configured
     * trusted proxy — prevents clients from spoofing their IP.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (rateLimitProps.trustedProxiesAsList().contains(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    private int resolveLimit(String uri) {
        if (uri.startsWith("/api/bookings")) return rateLimitProps.writePerMinute();
        if (uri.contains("/reviews")) return rateLimitProps.reviewPerMinute();
        if (uri.startsWith("/api/invoices") || uri.startsWith("/api/payments/invoices")) {
            return rateLimitProps.writePerMinute();
        }
        if (uri.startsWith("/api/contracts")) return rateLimitProps.writePerMinute();
        if (uri.startsWith("/api/agencies")) return rateLimitProps.reviewPerMinute();
        if (uri.startsWith("/api/ai/tools")) return rateLimitProps.aiToolsPerMinute();
        if (uri.startsWith("/api/ai")) return rateLimitProps.aiPerMinute();
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

    @Scheduled(fixedRate = 60_000)
    void evictExpiredBuckets() {
        long now = System.nanoTime();
        buckets.entrySet().removeIf(e -> now - e.getValue().lastAccessNanos > EXPIRY_NANOS);
        // Soft cap: if still oversized after normal eviction, force-compact
        if (buckets.size() > MAX_BUCKETS) {
            buckets.entrySet().removeIf(e -> now - e.getValue().lastAccessNanos > EXPIRY_NANOS / 2);
        }
    }
}
