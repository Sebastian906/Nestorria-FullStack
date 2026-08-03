package com.nestorria.server.common.event;

public record InvoicePaidEvent(
    String invoiceId,
    String userId
) {}
