package com.nestorria.server.common.outbox.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.common.outbox.EventHandler;
import com.nestorria.server.modules.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEventHandler implements EventHandler<NotificationEvent> {

    private final NotificationService notificationService;

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
        notificationService.createNotification(event);
        log.debug("Notificación persistida: type={}, userId={}",
            event.type(), event.userId());
    }
}
