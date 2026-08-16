package com.nestorria.server.modules.payment.dto;

import java.util.List;

import com.nestorria.server.modules.payment.InvoicePaymentAllocator;

public record PaymentAllocationResponse(
    List<InvoiceAllocationResponse> allocations,
    long totalAllocated,
    long unallocatedAmount,
    int invoiceCount
) {
    public static PaymentAllocationResponse fromResult(
            InvoicePaymentAllocator.AllocationResult result) {
        var allocations = result.allocations().stream()
            .map(InvoiceAllocationResponse::fromAllocation)
            .toList();
        long totalAllocated = result.allocations().stream()
            .mapToLong(InvoicePaymentAllocator.InvoiceAllocation::allocated)
            .sum();
        return new PaymentAllocationResponse(
            allocations,
            totalAllocated,
            result.unallocatedAmount(),
            result.allocations().size()
        );
    }
}
