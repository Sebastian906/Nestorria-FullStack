package com.nestorria.server.common.ai;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro de autenticación para endpoints internos de herramientas del LLM.
 * Valida X-API-Key header. Si es válido, establece un Authentication
 * con rol ROLE_AI_SERVICE para que SecurityFilterChain lo acepte.
 * Los endpoints de tools son internos: solo ai-service los llama.
 * No requieren JWT de Clerk — solo la API key configurada.
 */
@Component
public class ToolEndpointAuthFilter extends OncePerRequestFilter {

    private final AiServiceProperties aiServiceProperties;

    public ToolEndpointAuthFilter(AiServiceProperties aiServiceProperties) {
        this.aiServiceProperties = aiServiceProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Only apply to /api/ai/tools/** endpoints
        if (!uri.startsWith("/api/ai/tools")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-API-Key");

        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"timestamp\":\"" + java.time.Instant.now()
                + "\",\"message\":\"API key requerida para endpoints de herramientas\"}");
            return;
        }

        if (!aiServiceProperties.hasApiKey()
                || !java.util.Objects.equals(apiKey, aiServiceProperties.apiKey())) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"timestamp\":\"" + java.time.Instant.now()
                + "\",\"message\":\"API key inválida\"}");
            return;
        }

        // Set authentication so SecurityFilterChain allows the request
        var authorities = java.util.List.of(new SimpleGrantedAuthority("ROLE_AI_SERVICE"));
        var auth = new UsernamePasswordAuthenticationToken("ai-service", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
