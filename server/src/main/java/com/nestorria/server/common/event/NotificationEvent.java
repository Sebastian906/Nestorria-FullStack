package com.nestorria.server.common.event;

import com.nestorria.server.modules.notification.NotificationType;

public record NotificationEvent(
    String userId,
    NotificationType type,
    String title,
    String message,
    String referenceType,
    String referenceId
) {}
