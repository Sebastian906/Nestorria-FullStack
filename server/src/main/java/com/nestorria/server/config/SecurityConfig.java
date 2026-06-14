package com.nestorria.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

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
    public SecurityFilterChain filterChain(HttpSecurity http, UserProvisioningFilter provisioningFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .addFilterAfter(provisioningFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}