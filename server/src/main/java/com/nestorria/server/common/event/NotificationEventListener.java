package com.nestorria.server.common.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.nestorria.server.modules.notification.NotificationService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("taskExecutor")
    @EventListener
    public void handleNotificationEvent(NotificationEvent event) {
        try {
            notificationService.createNotification(event);
            log.info("Notificación persistida: type={}, userId={}", event.type(), event.userId());
        } catch (Exception e) {
            log.error("Error al persistir notificación (userId={}, type={}): {}",
                event.userId(), event.type(), e.getMessage());
        }
    }
}
