package com.nestorria.server.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    MailProperties mail,
    String currency
) {
    public record MailProperties(String sender) {}
}
