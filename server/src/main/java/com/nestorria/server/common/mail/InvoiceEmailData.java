package com.nestorria.server.common.mail;

import java.time.LocalDate;

public record InvoiceEmailData(
    String invoiceId,
    String invoiceNumber,
    String userEmail,
    String propertyAddress,
    long subtotal,
    long tax,
    long total,
    long lateFee,
    long amountDue,
    String currency,
    LocalDate issueDate,
    LocalDate dueDate,
    String status
) {}
