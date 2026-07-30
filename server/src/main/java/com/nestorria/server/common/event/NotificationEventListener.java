package com.nestorria.server.common.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
