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
        // Extract JWT from Authorization header: Authorization: Bearer <token>
        String token = extractTokenFromAuthorizationHeader(request);
        if (token == null) return false;

        try {
            Jwt jwt = jwtDecoder.decode(token);
            attributes.put("userId", jwt.getSubject());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractTokenFromAuthorizationHeader(ServerHttpRequest request) {
        // Get the original Servlet request to access HTTP headers
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        String authorization = servletRequest.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
