package com.nestorria.server.common.outbox;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
