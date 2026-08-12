package com.nestorria.server.common.outbox.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.common.outbox.EventHandler;
import com.nestorria.server.common.websocket.NotificationWebSocketMessage;
import com.nestorria.server.common.websocket.WebSocketNotificationService;
import com.nestorria.server.modules.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEventHandler implements EventHandler<NotificationEvent> {

    private final NotificationService notificationService;
    private final WebSocketNotificationService webSocketService;

    @Override
    public String getEventType() {
        return "NotificationEvent";
    }

    @Override
    public Class<NotificationEvent> getPayloadClass() {
        return NotificationEvent.class;
    }

    @Override
    @Transactional
    public void handle(NotificationEvent event) {
        // 1. Persistir notificación en base de datos (a través del servicio existente)
        notificationService.createNotification(event);
        log.debug("Notificación persistida: type={}, userId={}", event.type(), event.userId());

        // 2. Publicar vía WebSocket DESPUÉS del commit transaccional
        // Usamos los datos del evento directamente (evita query adicional a BD)
        long unreadCount = notificationService.getUnreadCount(event.userId());
        
        // Crear mensaje WebSocket usando builder pattern desde los datos del evento
        NotificationWebSocketMessage notificationMsg = NotificationWebSocketMessage.builder()
            .type(event.type().name())
            .title(event.title())
            .message(event.message())
            .referenceType(event.referenceType())
            .referenceId(event.referenceId())
            .isRead(false)
            .build();
        
        // Publicar al usuario específico (el userId del evento coincide con el principal
        // setado por WebSocketAuthInterceptor en el handshake)
        webSocketService.sendNotificationToUser(event.userId(), notificationMsg);
        
        // Publicar actualización del conteo de no leídas
        webSocketService.sendUnreadCountUpdate(event.userId(), unreadCount);
        
        log.debug("Notificación push WebSocket enviada a userId={}, unreadCount={}", 
            event.userId(), unreadCount);
    }
}
