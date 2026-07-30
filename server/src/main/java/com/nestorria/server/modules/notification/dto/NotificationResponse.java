package com.nestorria.server.modules.notification.dto;

import java.time.Instant;

import com.nestorria.server.modules.notification.Notification;
import com.nestorria.server.modules.notification.NotificationType;

public record NotificationResponse(
    String id,
    NotificationType type,
    String title,
    String message,
    String referenceType,
    String referenceId,
    boolean isRead,
    Instant createdAt
) {
    public static NotificationResponse fromEntity(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getReferenceType(),
            notification.getReferenceId(),
            notification.isRead(),
            notification.getCreatedAt()
        );
    }
}
