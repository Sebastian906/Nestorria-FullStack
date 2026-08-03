package com.nestorria.server.modules.payment.dto;

import java.time.Instant;

import com.nestorria.server.modules.payment.PaymentMethod;
import com.nestorria.server.modules.payment.PaymentTransaction;
import com.nestorria.server.modules.payment.TransactionStatus;

public record PaymentResponse(
    String id,
    long amount,
    String currency,
    PaymentMethod paymentMethod,
    String gatewayReference,
    TransactionStatus status,
    Instant paidAt,
    String failureMessage,
    Instant createdAt
) {
    public static PaymentResponse fromEntity(PaymentTransaction transaction) {
        return new PaymentResponse(
            transaction.getId(),
            transaction.getAmount(),
            transaction.getCurrency(),
            transaction.getPaymentMethod(),
            transaction.getGatewayReference(),
            transaction.getStatus(),
            transaction.getPaidAt(),
            transaction.getFailureMessage(),
            transaction.getCreatedAt()
        );
    }
}
