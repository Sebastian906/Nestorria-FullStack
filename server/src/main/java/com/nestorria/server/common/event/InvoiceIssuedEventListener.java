package com.nestorria.server.common.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nestorria.server.common.config.AppProperties;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.common.mail.EmailService;
import com.nestorria.server.modules.notification.NotificationType;
import com.nestorria.server.modules.payment.Invoice;
import com.nestorria.server.modules.payment.InvoiceRepository;
import com.nestorria.server.modules.payment.InvoiceService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class InvoiceIssuedEventListener {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties appProperties;

    public InvoiceIssuedEventListener(
            InvoiceRepository invoiceRepository,
            InvoiceService invoiceService,
            EmailService emailService,
            ApplicationEventPublisher eventPublisher,
            AppProperties appProperties) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.emailService = emailService;
        this.eventPublisher = eventPublisher;
        this.appProperties = appProperties;
    }

    @Async("taskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInvoiceIssued(InvoiceIssuedEvent event) {
        try {
            Invoice invoice = invoiceRepository.findById(event.invoiceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Factura no encontrada: " + event.invoiceId()));

            emailService.sendInvoiceIssuedEmail(invoiceService.buildInvoiceEmailData(invoice));

            eventPublisher.publishEvent(new NotificationEvent(
                event.userId(),
                NotificationType.INVOICE_ISSUED,
                NotificationType.INVOICE_ISSUED.defaultTitle(),
                "Se ha emitido la factura %s por un monto de %s.".formatted(
                    invoice.getInvoiceNumber(), EmailService.formatAmount(invoice.getTotal(), invoice.getCurrency())),
                "invoice",
                invoice.getId()));

            log.info("Side effects de factura emitida procesados: invoiceId={}", event.invoiceId());
        } catch (Exception e) {
            log.error("Error al procesar side effects de factura emitida (invoiceId={}): {}",
                event.invoiceId(), e.getMessage());
        }
    }
}
