package com.nestorria.server.common.websocket;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class WebSocketAuthInterceptor extends HttpSessionHandshakeInterceptor {
    private final JwtDecoder jwtDecoder;

    public WebSocketAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) return false;

        try {
            Jwt jwt = jwtDecoder.decode(token);
            attributes.put("userId", jwt.getSubject());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractToken(ServerHttpRequest request) {
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();

        // 1. Authorization header (funciona con SockJS XHR transport)
        String authorization = servletRequest.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }

        // 2. Query parameter ?token=xxx (necesario para WebSocket raw)
        String queryToken = servletRequest.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken.trim();
        }

        return null;
    }
}
