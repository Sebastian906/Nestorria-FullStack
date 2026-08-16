package com.nestorria.server.modules.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AllocatePaymentRequest(
    @NotNull @Min(1)
    long amountCents
) {}
