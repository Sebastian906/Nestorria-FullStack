package com.nestorria.server.common.outbox.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.event.InvoiceIssuedEvent;
import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.common.mail.EmailService;
import com.nestorria.server.common.outbox.EventHandler;
import com.nestorria.server.common.outbox.OutboxEventService;
import com.nestorria.server.modules.notification.NotificationType;
import com.nestorria.server.modules.payment.Invoice;
import com.nestorria.server.modules.payment.InvoiceRepository;
import com.nestorria.server.modules.payment.InvoiceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class InvoiceIssuedEventHandler implements EventHandler<InvoiceIssuedEvent> {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final EmailService emailService;
    private final OutboxEventService outboxEventService;

    @Override
    public String getEventType() {
        return "InvoiceIssuedEvent";
    }

    @Override
    public Class<InvoiceIssuedEvent> getPayloadClass() {
        return InvoiceIssuedEvent.class;
    }

    @Override
    @Transactional
    public void handle(InvoiceIssuedEvent event) {
        Invoice invoice = invoiceRepository.findById(event.invoiceId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Factura no encontrada: " + event.invoiceId()));

        // Enviar email de factura emitida
        emailService.sendInvoiceIssuedEmail(invoiceService.buildInvoiceEmailData(invoice));

        // Publicar notificación via outbox (se persiste en esta misma transacción)
        outboxEventService.publish(
            new NotificationEvent(
                event.userId(),
                NotificationType.INVOICE_ISSUED,
                NotificationType.INVOICE_ISSUED.defaultTitle(),
                "Se ha emitido la factura %s por un monto de %s.".formatted(
                    invoice.getInvoiceNumber(),
                    EmailService.formatAmount(invoice.getTotal(), invoice.getCurrency())),
                "invoice",
                invoice.getId()),
            "Invoice",
            invoice.getId());

        log.info("Side effects de factura emitida procesados: invoiceId={}", event.invoiceId());
    }
}
