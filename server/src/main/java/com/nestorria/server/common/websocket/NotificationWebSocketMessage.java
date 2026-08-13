package com.nestorria.server.common.websocket;

import java.time.Instant;

import com.nestorria.server.modules.notification.Notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationWebSocketMessage {

    private final String id;
    private final String type;
    private final String title;
    private final String message;
    private final String referenceType;
    private final String referenceId;
    @JsonProperty("isRead")
    private final boolean isRead;

    private final Instant createdAt;

    public NotificationWebSocketMessage(String id, String type, String title, String message,
                                       String referenceType, String referenceId, boolean isRead,
                                       Instant createdAt) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.message = message;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public NotificationWebSocketMessage(Notification notification) {
        this.id = notification.getId();
        this.type = notification.getType().name();
        this.title = notification.getTitle();
        this.message = notification.getMessage();
        this.referenceType = notification.getReferenceType();
        this.referenceId = notification.getReferenceId();
        this.isRead = notification.isRead();
        this.createdAt = notification.getCreatedAt();
    }

    // Constructor vacío para deserialización JSON en frontend
    public NotificationWebSocketMessage() {
        this.id = "";
        this.type = "";
        this.title = "";
        this.message = "";
        this.referenceType = "";
        this.referenceId = "";
        this.isRead = false;
        this.createdAt = Instant.EPOCH;
    }
}
