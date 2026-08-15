package com.nestorria.server.modules.payment;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.config.AppProperties;
import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.common.mail.EmailService;
import com.nestorria.server.common.mail.InvoiceEmailData;
import com.nestorria.server.common.outbox.OutboxEventService;
import com.nestorria.server.modules.notification.NotificationType;

import lombok.extern.slf4j.Slf4j;

/**
 * Worker separado para procesamiento transaccional de facturas.
 * Separado de InvoiceService para evitar self-invocation de @Transactional:
 * cuando un método @Transactional llama a otro @Transactional dentro
 * de la misma clase, el proxy de Spring no intercepta la llamada interna
 * y la transacción no funciona correctamente.
 */
@Component
@Slf4j
class InvoiceTransactionWorker {

    private final InvoiceRepository invoiceRepository;
    private final EmailService emailService;
    private final OutboxEventService outboxEventService;
    private final AppProperties appProperties;

    InvoiceTransactionWorker(
            InvoiceRepository invoiceRepository,
            EmailService emailService,
            OutboxEventService outboxEventService,
            AppProperties appProperties) {
        this.invoiceRepository = invoiceRepository;
        this.emailService = emailService;
        this.outboxEventService = outboxEventService;
        this.appProperties = appProperties;
    }

    /**
     * Procesa una factura vencida en su propia transacción.
     * Recarga la factura dentro de la transacción para garantizar
     * que el update y la publicación outbox compartan el mismo boundary.
     */
    @Transactional
    public void processOverdueInvoice(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new IllegalStateException(
                "Invoice not found during async processing: " + invoiceId));

        double lateFeePercentage = appProperties.invoice().lateFeePercentage();
        long lateFee = (long) (invoice.getTotal() * lateFeePercentage);
        invoice.setStatus(InvoiceStatus.OVERDUE);
        invoice.setLateFee(lateFee);
        invoiceRepository.save(invoice);

        emailService.sendInvoiceOverdueEmail(buildEmailData(invoice));

        outboxEventService.publish(
            new NotificationEvent(
                invoice.getBooking().getUser().getId(),
                NotificationType.INVOICE_OVERDUE,
                NotificationType.INVOICE_OVERDUE.defaultTitle(),
                "La factura %s ha vencido. Se ha aplicado un cargo por mora de %s.".formatted(
                    invoice.getInvoiceNumber(),
                    EmailService.formatAmount(lateFee, invoice.getCurrency())),
                "invoice",
                invoice.getId()
            ),
            "Invoice",
            invoice.getId());

        log.info("Factura marcada como vencida: {} (lateFee: {} cents)",
            invoice.getInvoiceNumber(), lateFee);
    }

    /**
     * Procesa un recordatorio de factura en su propia transacción.
     */
    @Transactional
    public void processReminder(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new IllegalStateException(
                "Invoice not found during async reminder: " + invoiceId));

        emailService.sendInvoiceReminderEmail(buildEmailData(invoice));

        outboxEventService.publish(
            new NotificationEvent(
                invoice.getBooking().getUser().getId(),
                NotificationType.INVOICE_ISSUED,
                "Recordatorio de factura",
                "La factura %s vence mañana. Total a pagar: %s.".formatted(
                    invoice.getInvoiceNumber(),
                    EmailService.formatAmount(invoice.getTotal(), invoice.getCurrency())),
                "invoice",
                invoice.getId()
            ),
            "Invoice",
            invoice.getId());

        log.info("Recordatorio enviado para factura: {}", invoice.getInvoiceNumber());
    }

    private InvoiceEmailData buildEmailData(Invoice invoice) {
        return new InvoiceEmailData(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            invoice.getBooking().getUser().getEmail(),
            invoice.getBooking().getProperty().getAddress(),
            invoice.getSubtotal(),
            invoice.getTax(),
            invoice.getTotal(),
            invoice.getLateFee(),
            invoice.getAmountDue(),
            invoice.getCurrency(),
            invoice.getIssueDate(),
            invoice.getDueDate(),
            invoice.getStatus().name()
        );
    }
}
