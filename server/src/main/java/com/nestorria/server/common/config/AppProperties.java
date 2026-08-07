package com.nestorria.server.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        MailProperties mail,
        String currency,
        InvoiceProperties invoice,
        StripeProperties stripe,
        RateLimitProperties rateLimit) {
    public record MailProperties(String sender) {
    }

    public record InvoiceProperties(
            double taxRate,
            int dueDays,
            double lateFeePercentage) {
    }

    public record StripeProperties(String allowedOrigins) {
        public List<String> originsAsList() {
            return List.of(allowedOrigins.split(","));
        }

        public void validate() {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                throw new IllegalStateException(
                        "app.stripe.allowed-origins no puede estar vacío. "
                                + "Configura al menos un origen permitido (ej. http://localhost:5173)");
            }
            if (originsAsList().stream().anyMatch(String::isBlank)) {
                throw new IllegalStateException(
                        "app.stripe.allowed-origins contiene valores vacíos. "
                                + "Revisa la configuración (valores separados por coma, sin espacios extra)");
            }
        }
    }

    public record RateLimitProperties(
            boolean enabled,
            int readPerMinute,
            int writePerMinute,
            int reviewPerMinute,
            int stripePerMinute,
            int publicReadPerMinute,
            int searchPerMinute,
            String trustedProxies) {
        public java.util.List<String> trustedProxiesAsList() {
            if (trustedProxies == null || trustedProxies.isBlank()) {
                return java.util.List.of();
            }
            return java.util.List.of(trustedProxies.split(","));
        }
    }
}
