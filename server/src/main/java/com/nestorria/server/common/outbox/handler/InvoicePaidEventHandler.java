package com.nestorria.server.common.outbox.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.event.InvoicePaidEvent;
import com.nestorria.server.common.event.NotificationEvent;
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
public class InvoicePaidEventHandler implements EventHandler<InvoicePaidEvent> {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final EmailService emailService;
    private final OutboxEventService outboxEventService;

    @Override
    public String getEventType() {
        return "InvoicePaidEvent";
    }

    @Override
    public Class<InvoicePaidEvent> getPayloadClass() {
        return InvoicePaidEvent.class;
    }

    @Override
    @Transactional
    public void handle(InvoicePaidEvent event) {
        Invoice invoice = invoiceRepository.findById(event.invoiceId())
            .orElseThrow(() -> new com.nestorria.server.common.exception.ResourceNotFoundException(
                "Factura no encontrada: " + event.invoiceId()));

        // Enviar email de pago confirmado
        emailService.sendInvoicePaidEmail(invoiceService.buildInvoiceEmailData(invoice));

        // Publicar notificación via outbox (se persiste en esta misma transacción)
        outboxEventService.publish(
            new NotificationEvent(
                event.userId(),
                NotificationType.INVOICE_PAID,
                NotificationType.INVOICE_PAID.defaultTitle(),
                "La factura %s ha sido pagada exitosamente.".formatted(
                    invoice.getInvoiceNumber()),
                "invoice",
                invoice.getId()),
            "Invoice",
            invoice.getId());

        log.info("Side effects de pago procesados: invoiceId={}", event.invoiceId());
    }
}
