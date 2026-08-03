package com.nestorria.server.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        MailProperties mail,
        String currency,
        InvoiceProperties invoice,
        StripeProperties stripe) {
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
    }
}
