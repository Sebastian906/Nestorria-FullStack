package com.nestorria.server.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        MailProperties mail,
        String currency,
        InvoiceProperties invoice) {
    public record MailProperties(String sender) {
    }

    public record InvoiceProperties(
            double taxRate,
            int dueDays,
            double lateFeePercentage) {
    }
}
