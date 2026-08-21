package com.nestorria.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import com.nestorria.server.modules.user.UserProvisioningFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${clerk.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${clerk.issuer-uri}")
    private String issuerUri;

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            UserProvisioningFilter provisioningFilter,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/properties/me").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/properties/nearby").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/properties/*/reviews").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/stripe/webhook").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/agencies").permitAll()
                // Actuator: health público para load balancers, el resto autenticado
                .requestMatchers("/actuator/health").permitAll()
                // WebSocket: auth manejada por WebSocketAuthInterceptor, no por BearerTokenAuthenticationFilter
                .requestMatchers("/ws").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .addFilterAfter(provisioningFilter, BearerTokenAuthenticationFilter.class);
        http
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"timestamp\":\"" + java.time.Instant.now() + "\",\"message\":\"No autenticado: " + authException.getMessage() + "\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"timestamp\":\"" + java.time.Instant.now() + "\",\"message\":\"Acceso denegado: " + accessDeniedException.getMessage() + "\"}");
                })
            );
        return http.build();
    }
}