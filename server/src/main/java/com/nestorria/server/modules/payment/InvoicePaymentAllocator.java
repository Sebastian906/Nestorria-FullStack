package com.nestorria.server.modules.payment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.nestorria.server.common.algorithm.GreedyUtils;

/**
 * Asigna un monto de pago a múltiples facturas usando estrategia greedy.
 * Estrategia:
 * 1. Facturas OVERDUE primero (mayor prioridad — acumulan late fees)
 * 2. Dentro del mismo estado, fecha de vencimiento más temprana primero
 * 3. Dentro de la misma fecha, monto menor primero (limpia facturas más rápido)
 * ¿Es óptimo? Es una heurística greedy. Minimiza late fees al priorizar facturas vencidas,
 * pero la optimalidad depende de la tasa de late fee y el patrón de pagos futuros.
 * Para el caso típico (un pago cubre múltiples facturas), produce una muy buena solución.
 * Time:  O(n log n) — dominado por sorting
 * Space: O(n)
 */
public final class InvoicePaymentAllocator {

    private InvoicePaymentAllocator() {}

    // Resultado de la asignación de un pago a múltiples facturas.
    public record AllocationResult(
        List<InvoiceAllocation> allocations,
        long unallocatedAmount
    ) {}

    // Resultado de asignación individual por factura.
    public record InvoiceAllocation(
        String invoiceId,
        String invoiceNumber,
        long amountDue,
        long paidAmount,
        long allocated,
        InvoiceStatus status
    ) {}

    /**
     * Asigna un monto de pago a las facturas impagas usando greedy.
     * @param paymentCents    — monto total a asignar (en centavos)
     * @param unpaidInvoices  — facturas que aceptan pago (PENDING o OVERDUE)
     * @return resultado con asignaciones individuales y monto sobrante
     */
    public static AllocationResult allocate(long paymentCents, List<Invoice> unpaidInvoices) {
        if (paymentCents <= 0 || unpaidInvoices == null || unpaidInvoices.isEmpty()) {
            return new AllocationResult(List.of(), paymentCents);
        }

        // Greedy: ordenar por prioridad (overdue first, earliest due, smallest amount)
        List<Invoice> sorted = GreedyUtils.sortByPriority(
            unpaidInvoices,
            // 1. OVERDUE primero (status == OVERDUE → prioridad 0, else → 1)
            Comparator.comparing((Invoice i) ->
                i.getStatus() == InvoiceStatus.OVERDUE ? 0 : 1),
            // 2. Fecha de vencimiento más temprana
            Comparator.comparing(Invoice::getDueDate),
            // 3. Monto pendiente menor primero
            Comparator.comparingLong(Invoice::getOutstandingAmount)
        );

        List<InvoiceAllocation> allocations = new ArrayList<>();
        long remaining = paymentCents;

        for (Invoice invoice : sorted) {
            if (remaining <= 0) break;

            long outstanding = invoice.getOutstandingAmount();
            if (outstanding <= 0) continue;

            long allocated = Math.min(remaining, outstanding);
            allocations.add(new InvoiceAllocation(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getAmountDue(),
                invoice.getPaidAmount(),
                allocated,
                invoice.getStatus()
            ));
            remaining -= allocated;
        }

        return new AllocationResult(allocations, remaining);
    }
}
