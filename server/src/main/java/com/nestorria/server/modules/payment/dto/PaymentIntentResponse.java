package com.nestorria.server.modules.payment.dto;

public record PaymentIntentResponse(
    String invoiceId,
    String clientSecret,
    long amount,
    String currency
) {}
