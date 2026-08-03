package com.nestorria.server.modules.payment.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.nestorria.server.modules.payment.Invoice;
import com.nestorria.server.modules.payment.InvoiceStatus;

public record InvoiceResponse(
    String id,
    String invoiceNumber,
    String bookingId,
    LocalDate issueDate,
    LocalDate dueDate,
    long subtotal,
    long tax,
    long total,
    long lateFee,
    long amountDue,
    String currency,
    InvoiceStatus status,
    List<PaymentResponse> paymentTransactions,
    Instant createdAt
) {
    public static InvoiceResponse fromEntity(Invoice invoice) {
        return new InvoiceResponse(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            invoice.getBooking().getId(),
            invoice.getIssueDate(),
            invoice.getDueDate(),
            invoice.getSubtotal(),
            invoice.getTax(),
            invoice.getTotal(),
            invoice.getLateFee(),
            invoice.getAmountDue(),
            invoice.getCurrency(),
            invoice.getStatus(),
            invoice.getPaymentTransactions().stream()
                .map(PaymentResponse::fromEntity)
                .toList(),
            invoice.getCreatedAt()
        );
    }
}
