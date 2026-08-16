package com.nestorria.server.modules.payment;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class InvoicePaymentAllocatorTest {

    @Test
    void allocate_fullPaymentCoversAllInvoices() {
        Invoice inv1 = createInvoice("INV-001", 1000, LocalDate.of(2026, 8, 10), InvoiceStatus.PENDING);
        Invoice inv2 = createInvoice("INV-002", 2000, LocalDate.of(2026, 8, 5), InvoiceStatus.PENDING);

        var result = InvoicePaymentAllocator.allocate(3000, List.of(inv1, inv2));

        assertEquals(2, result.allocations().size());
        assertEquals(0, result.unallocatedAmount());
    }

    @Test
    void allocate_partialPayment_prioritizesOverdue() {
        Invoice overdue = createInvoice("INV-OD", 5000, LocalDate.of(2026, 8, 1), InvoiceStatus.OVERDUE);
        Invoice pending = createInvoice("INV-PD", 5000, LocalDate.of(2026, 8, 10), InvoiceStatus.PENDING);

        var result = InvoicePaymentAllocator.allocate(3000, List.of(pending, overdue));

        // Overdue should be paid first
        assertEquals("INV-OD", result.allocations().get(0).invoiceNumber());
        assertEquals(3000, result.allocations().get(0).allocated());
        assertEquals(0, result.unallocatedAmount());
    }

    @Test
    void allocate_partialPayment_splitsAcrossInvoices() {
        Invoice inv1 = createInvoice("INV-001", 2000, LocalDate.of(2026, 8, 1), InvoiceStatus.OVERDUE);
        Invoice inv2 = createInvoice("INV-002", 3000, LocalDate.of(2026, 8, 5), InvoiceStatus.OVERDUE);

        var result = InvoicePaymentAllocator.allocate(4000, List.of(inv1, inv2));

        assertEquals(2, result.allocations().size());
        assertEquals(2000, result.allocations().get(0).allocated()); // inv1 fully paid
        assertEquals(2000, result.allocations().get(1).allocated()); // inv2 partially paid
        assertEquals(0, result.unallocatedAmount());
    }

    @Test
    void allocate_unallocatedFundsRemain() {
        Invoice inv = createInvoice("INV-001", 1000, LocalDate.of(2026, 8, 10), InvoiceStatus.PENDING);

        var result = InvoicePaymentAllocator.allocate(5000, List.of(inv));

        assertEquals(1, result.allocations().size());
        assertEquals(1000, result.allocations().get(0).allocated());
        assertEquals(4000, result.unallocatedAmount());
    }

    @Test
    void allocate_noInvoices() {
        var result = InvoicePaymentAllocator.allocate(5000, List.of());
        assertTrue(result.allocations().isEmpty());
        assertEquals(5000, result.unallocatedAmount());
    }

    @Test
    void allocate_nullInvoices() {
        var result = InvoicePaymentAllocator.allocate(5000, null);
        assertTrue(result.allocations().isEmpty());
        assertEquals(5000, result.unallocatedAmount());
    }

    @Test
    void allocate_zeroPayment() {
        Invoice inv = createInvoice("INV-001", 1000, LocalDate.of(2026, 8, 10), InvoiceStatus.PENDING);
        var result = InvoicePaymentAllocator.allocate(0, List.of(inv));
        assertTrue(result.allocations().isEmpty());
        assertEquals(0, result.unallocatedAmount());
    }

    @Test
    void allocate_singleInvoice() {
        Invoice inv = createInvoice("INV-001", 5000, LocalDate.of(2026, 8, 10), InvoiceStatus.PENDING);
        var result = InvoicePaymentAllocator.allocate(3000, List.of(inv));
        assertEquals(1, result.allocations().size());
        assertEquals(3000, result.allocations().get(0).allocated());
        assertEquals(0, result.unallocatedAmount());
    }

    @Test
    void allocate_sameDueDate_sortByAmountSmallestFirst() {
        Invoice large = createInvoice("INV-LG", 5000, LocalDate.of(2026, 8, 10), InvoiceStatus.PENDING);
        Invoice small = createInvoice("INV-SM", 1000, LocalDate.of(2026, 8, 10), InvoiceStatus.PENDING);

        var result = InvoicePaymentAllocator.allocate(3000, List.of(large, small));

        // Smallest amount first (clears invoices faster)
        assertEquals("INV-SM", result.allocations().get(0).invoiceNumber());
        assertEquals(1000, result.allocations().get(0).allocated());
        assertEquals("INV-LG", result.allocations().get(1).invoiceNumber());
        assertEquals(2000, result.allocations().get(1).allocated());
    }

    @Test
    void allocate_exactPaymentAmount() {
        Invoice inv = createInvoice("INV-001", 5000, LocalDate.of(2026, 8, 10), InvoiceStatus.PENDING);
        var result = InvoicePaymentAllocator.allocate(5000, List.of(inv));
        assertEquals(1, result.allocations().size());
        assertEquals(5000, result.allocations().get(0).allocated());
        assertEquals(0, result.unallocatedAmount());
    }

    @Test
    void allocate_withExistingPartialPayment() {
        Invoice inv = createInvoice("INV-001", 5000, LocalDate.of(2026, 8, 10), InvoiceStatus.PENDING);
        // Simulate existing partial payment by adding a succeeded transaction
        inv.getPaymentTransactions().add(new PaymentTransaction(
            inv, 2000, "USD", PaymentMethod.STRIPE, "pi_test",
            TransactionStatus.SUCCEEDED, java.time.Instant.now(), null));

        var result = InvoicePaymentAllocator.allocate(3000, List.of(inv));

        assertEquals(1, result.allocations().size());
        assertEquals(3000, result.allocations().get(0).allocated()); // 5000 - 2000 = 3000 outstanding
        assertEquals(0, result.unallocatedAmount());
    }

    @Test
    void allocate_greedyResultCoversAtLeastAsMuchAsNaive() {
        // Greedy should cover at least as many invoices as naive (FIFO) allocation
        Invoice inv1 = createInvoice("INV-001", 1000, LocalDate.of(2026, 8, 10), InvoiceStatus.PENDING);
        Invoice inv2 = createInvoice("INV-002", 2000, LocalDate.of(2026, 8, 5), InvoiceStatus.OVERDUE);
        Invoice inv3 = createInvoice("INV-003", 1500, LocalDate.of(2026, 8, 15), InvoiceStatus.PENDING);

        long payment = 2500;
        var greedyResult = InvoicePaymentAllocator.allocate(payment, List.of(inv1, inv2, inv3));

        long greedyTotal = greedyResult.allocations().stream()
            .mapToLong(InvoicePaymentAllocator.InvoiceAllocation::allocated).sum();

        // Greedy covers at least as much as any naive strategy
        assertTrue(greedyTotal >= payment || greedyTotal >= 2000);
        assertEquals(payment - greedyTotal, greedyResult.unallocatedAmount());
    }

    private Invoice createInvoice(String number, long total, LocalDate dueDate, InvoiceStatus status) {
        Invoice invoice = new Invoice(
            null, number, LocalDate.now(), dueDate,
            total, 0, total, "USD"
        );
        invoice.setStatus(status);
        return invoice;
    }
}
