package com.nestorria.server.common.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.nestorria.server.modules.notification.Notification;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Send a notification to a specific user via WebSocket.
     * Usa /user/{userId}/topic/notifications basada en el Principal setado en el handshake interceptor.
     */
    public void sendNotificationToUser(String userId, Notification notification) {
        messagingTemplate.convertAndSendToUser(
            userId,
            "/topic/notifications",
            notificationWebSocketMessage(notification)
        );
    }

    /**
     * Send a notification to a specific user via WebSocket using NotificationWebSocketMessage DTO.
     */
    public void sendNotificationToUser(String userId, NotificationWebSocketMessage message) {
        messagingTemplate.convertAndSendToUser(
            userId,
            "/topic/notifications",
            message
        );
    }

    /**
     * Send unread count update to a specific user.
     */
    public void sendUnreadCountUpdate(String userId, long unreadCount) {
        messagingTemplate.convertAndSendToUser(
            userId,
            "/topic/notifications/unread-count",
            new UnreadCountUpdate(unreadCount)
        );
    }

    /**
     * Broadcast to all connected users (e.g., system announcements).
     * Usa /topic/broadcast (sin user prefix).
     */
    public void broadcast(NotificationWebSocketMessage message) {
        messagingTemplate.convertAndSend("/topic/broadcast", message);
    }

    /**
     * Send a notification to a specific user via userId (alternative when principal not available).
     */
    public void sendNotificationByUserId(String userId, Notification notification) {
        messagingTemplate.convertAndSendToUser(
            userId,
            "/topic/notifications",
            notificationWebSocketMessage(notification)
        );
    }

    private NotificationWebSocketMessage notificationWebSocketMessage(Notification notification) {
        return new NotificationWebSocketMessage(notification);
    }

    public record UnreadCountUpdate(long count) {}
}
