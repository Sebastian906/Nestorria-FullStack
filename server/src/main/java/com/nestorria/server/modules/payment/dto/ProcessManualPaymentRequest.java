package com.nestorria.server.modules.payment.dto;

import com.nestorria.server.modules.payment.PaymentMethod;

import jakarta.validation.constraints.NotNull;

public record ProcessManualPaymentRequest(
    @NotNull PaymentMethod paymentMethod
) {
    public ProcessManualPaymentRequest {
        if (paymentMethod == PaymentMethod.STRIPE) {
            throw new IllegalArgumentException(
                "Use el endpoint de pago con Stripe para pagos con tarjeta");
        }
    }
}
