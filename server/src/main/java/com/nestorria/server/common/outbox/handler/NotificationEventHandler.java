package com.nestorria.server.common.outbox.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        var notification = notificationService.createNotification(event);
        log.debug("Notificación persistida: type={}, userId={}", event.type(), event.userId());

        // 2. Construir mensaje WebSocket a partir de la entidad persistida para incluir
        // el id y createdAt generados por JPA/Auditing (necesario para dedup y renderizado en cliente).
        long unreadCount = notificationService.getUnreadCount(event.userId());
        NotificationWebSocketMessage notificationMsg = new NotificationWebSocketMessage(notification);

        // 3. Difirir llamadas WebSocket al después del commit transaccional mediante
        // TransactionSynchronization.afterCommit, de modo que sendNotificationToUser y
        // sendUnreadCountUpdate ejecionen solo después de un commit exitoso.
        deferWebSocketPublishes(event.userId(), notificationMsg, unreadCount);
    }

    private void deferWebSocketPublishes(String userId, NotificationWebSocketMessage notificationMsg, long unreadCount) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Publicar al usuario específico después del commit exitoso
                webSocketService.sendNotificationToUser(userId, notificationMsg);
                // Publicar actualización del conteo de no leídas después del commit exitoso
                webSocketService.sendUnreadCountUpdate(userId, unreadCount);
                log.debug("Notificación push WebSocket enviada a userId={}, unreadCount={}", userId, unreadCount);
            }
        });
    }
}
