package com.nestorria.server.modules.payment.dto;

import com.nestorria.server.modules.payment.InvoicePaymentAllocator;
import com.nestorria.server.modules.payment.InvoiceStatus;

public record InvoiceAllocationResponse(
    String invoiceId,
    String invoiceNumber,
    long amountDue,
    long paidAmount,
    long outstandingBeforeAllocation,
    long allocated,
    InvoiceStatus status
) {
    public static InvoiceAllocationResponse fromAllocation(
            InvoicePaymentAllocator.InvoiceAllocation allocation) {
        return new InvoiceAllocationResponse(
            allocation.invoiceId(),
            allocation.invoiceNumber(),
            allocation.amountDue(),
            allocation.paidAmount(),
            allocation.amountDue() - allocation.paidAmount(),
            allocation.allocated(),
            allocation.status()
        );
    }
}
