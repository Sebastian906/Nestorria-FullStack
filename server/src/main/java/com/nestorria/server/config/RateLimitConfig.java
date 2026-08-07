package com.nestorria.server.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationPropertiesScan
public class RateLimitConfig {
    // AppProperties is now scanned and includes RateLimitProperties.
    // The RateLimitFilter reads from AppProperties directly.
    // No additional beans needed — RateLimitFilter is @Component.
}
