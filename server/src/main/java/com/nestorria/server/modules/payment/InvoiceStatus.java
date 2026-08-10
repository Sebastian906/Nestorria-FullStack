package com.nestorria.server.modules.payment;

import java.util.Set;

public enum InvoiceStatus {
    PENDING,
    PAID,
    OVERDUE,
    CANCELLED;

    /** Estados que permiten procesar un pago. */
    public static final Set<InvoiceStatus> PAYABLE = Set.of(PENDING, OVERDUE);
}
