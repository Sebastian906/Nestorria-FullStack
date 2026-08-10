package com.nestorria.server.common.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.notification.NotificationService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class NotificationEventListener {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(NotificationEvent event) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                notificationService.createNotification(event);
                log.info("Notificación persistida: type={}, userId={} (intento {})",
                    event.type(), event.userId(), attempt);
                return;
            } catch (ResourceNotFoundException e) {
                log.error("Error permanente al persistir notificación (userId={}, type={}): {}",
                    event.userId(), event.type(), e.getMessage());
                return;
            } catch (Exception e) {
                log.warn("Intento {}/{} fallido al persistir notificación (userId={}, type={}): {}",
                    attempt, MAX_RETRIES, event.userId(), event.type(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Interrumpido durante retry de notificación: {}", ie.getMessage());
                        return;
                    }
                }
            }
        }
        log.error("Fallo definitivo al persistir notificación (userId={}, type={}) después de {} intentos",
            event.userId(), event.type(), MAX_RETRIES);
    }
}
