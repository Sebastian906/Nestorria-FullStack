package com.nestorria.server.common.event;

public record InvoiceIssuedEvent(
    String invoiceId,
    String userId
) {}
