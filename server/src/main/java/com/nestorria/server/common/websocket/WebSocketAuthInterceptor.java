package com.nestorria.server.common.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Valida el JWT de Clerk en el frame STOMP CONNECT y lo convierte en el
 * Principal de la sesión WebSocket.
 *
 * El token viaja como header nativo Authorization del frame CONNECT
 * (connectHeaders en @stomp/stompjs), nunca en el query string del
 * handshake: un JWT de sesión en la URL quedaría expuesto en logs y
 * en el historial del navegador.
 */
@Component
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public WebSocketAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !SimpMessageType.CONNECT.equals(accessor.getMessageType())) {
            return message;
        }

        String token = extractToken(accessor);
        if (token == null) {
            log.warn("WebSocket CONNECT sin token de autenticación");
            throw new MessageDeliveryException("No se proporcionó token de autenticación");
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            accessor.setUser(() -> jwt.getSubject());
            return message;
        } catch (Exception e) {
            log.warn("WebSocket CONNECT rechazado: {}", e.getMessage());
            throw new MessageDeliveryException("Token inválido: " + e.getMessage());
        }
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return null;
    }
}
