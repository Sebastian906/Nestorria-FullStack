package com.nestorria.server.common.websocket;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
        // Extract token from query parameter ( ?token= )
        String token = extractTokenFromQuery(request);
        if (token == null) return false;

        try {
            Jwt jwt = jwtDecoder.decode(token);
            attributes.put("userId", jwt.getSubject());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractTokenFromQuery(ServerHttpRequest request) {
        // Get the original Servlet request to access query parameters
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        String queryString = servletRequest.getQueryString();
        if (queryString == null || !queryString.contains("token=")) {
            return null;
        }
        // Parse token parameter: ?token=xxx&other=yyy
        String[] params = queryString.split("&");
        for (String param : params) {
            if (param.startsWith("token=")) {
                String tokenValue = param.substring("token=".length());
                // Decode if URL-encoded
                try {
                    return URLDecoder.decode(tokenValue, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return tokenValue;
                }
            }
        }
        return null;
    }
}
