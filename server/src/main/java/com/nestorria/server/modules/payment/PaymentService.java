package com.nestorria.server.modules.payment;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.event.NotificationEvent;
import com.nestorria.server.common.exception.BadRequestException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.common.mail.EmailService;
import com.nestorria.server.modules.booking.Booking;
import com.nestorria.server.modules.notification.NotificationType;
import com.nestorria.server.modules.payment.dto.PaymentIntentResponse;
import com.nestorria.server.modules.payment.dto.PaymentResponse;
import com.nestorria.server.modules.payment.dto.ProcessManualPaymentRequest;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StripeClient stripeClient;
    private final InvoiceService invoiceService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public PaymentService(InvoiceRepository invoiceRepository,
                          PaymentTransactionRepository paymentTransactionRepository,
                          StripeClient stripeClient,
                          InvoiceService invoiceService,
                          EmailService emailService,
                          ApplicationEventPublisher eventPublisher) {
        this.invoiceRepository = invoiceRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.stripeClient = stripeClient;
        this.invoiceService = invoiceService;
        this.emailService = emailService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PaymentIntentResponse createPaymentIntent(String invoiceId, String userId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Factura no encontrada: " + invoiceId));

        if (!invoice.isParty(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes acceso a esta factura");
        }

        if (invoice.getStatus() != InvoiceStatus.PENDING
                && invoice.getStatus() != InvoiceStatus.OVERDUE) {
            throw new BadRequestException(
                "La factura no se puede pagar. Estado actual: " + invoice.getStatus());
        }

        if (invoice.getStatus() == InvoiceStatus.OVERDUE) {
            long lateFee = invoiceService.calculateLateFee(invoice);
            invoice.setLateFee(lateFee);
            invoiceRepository.save(invoice);
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("invoiceId", invoice.getId());
        metadata.put("invoiceNumber", invoice.getInvoiceNumber());
        metadata.put("bookingId", invoice.getBooking().getId());

        com.stripe.model.PaymentIntent paymentIntent = stripeClient.createPaymentIntent(
            invoice.getAmountDue(), invoice.getCurrency(), metadata);

        PaymentTransaction transaction = new PaymentTransaction(
            invoice,
            invoice.getAmountDue(),
            invoice.getCurrency(),
            PaymentMethod.STRIPE,
            paymentIntent.getId(),
            TransactionStatus.PENDING,
            null,
            null
        );
        paymentTransactionRepository.save(transaction);

        log.info("PaymentIntent creado para factura {}: {} (amount: {} cents)",
            invoiceId, paymentIntent.getId(), invoice.getAmountDue());

        return new PaymentIntentResponse(
            invoice.getId(),
            paymentIntent.getClientSecret(),
            invoice.getAmountDue(),
            invoice.getCurrency()
        );
    }

    @Transactional
    public void handleStripeWebhook(String payload, String sigHeader) {
        com.stripe.model.Event event = stripeClient.constructWebhookEvent(
            payload, sigHeader, webhookSecret);

        String eventType = event.getType();

        if ("checkout.session.completed".equals(eventType)) {
            handleCheckoutSessionCompleted(event);
        } else if ("payment_intent.succeeded".equals(eventType)) {
            handlePaymentIntentSucceeded(event);
        } else {
            log.info("Evento de webhook ignorado: {}", eventType);
        }
    }

    private void handleCheckoutSessionCompleted(com.stripe.model.Event event) {
        com.stripe.model.checkout.Session session =
            (com.stripe.model.checkout.Session) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new BadRequestException(
                    "No se pudo deserializar la Session de Checkout"));

        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || !metadata.containsKey("bookingId")) {
            log.warn("Checkout Session sin metadata de booking: {}", session.getId());
            return;
        }

        String bookingId = metadata.get("bookingId");
        String invoiceId = metadata.get("invoiceId");

        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Factura no encontrada: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            log.info("Factura ya pagada: {}", invoiceId);
            return;
        }

        PaymentTransaction transaction = new PaymentTransaction(
            invoice,
            invoice.getAmountDue(),
            invoice.getCurrency(),
            PaymentMethod.STRIPE,
            session.getPaymentIntent(),
            TransactionStatus.SUCCEEDED,
            java.time.Instant.now(),
            null
        );
        paymentTransactionRepository.save(transaction);

        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        Booking booking = invoice.getBooking();
        booking.markAsPaid();

        eventPublisher.publishEvent(new NotificationEvent(
            booking.getUser().getId(),
            NotificationType.INVOICE_PAID,
            NotificationType.INVOICE_PAID.defaultTitle(),
            "La factura %s ha sido pagada exitosamente.".formatted(
                invoice.getInvoiceNumber()),
            "invoice",
            invoice.getId()
        ));

        emailService.sendInvoicePaidEmail(invoiceService.buildInvoiceEmailData(invoice));

        log.info("Pago confirmado vía Checkout Session: factura {}, sesión {}",
            invoiceId, session.getId());
    }

    private void handlePaymentIntentSucceeded(com.stripe.model.Event event) {
        com.stripe.model.PaymentIntent paymentIntent =
            (com.stripe.model.PaymentIntent) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new BadRequestException(
                    "No se pudo deserializar el PaymentIntent"));

        String paymentIntentId = paymentIntent.getId();

        PaymentTransaction transaction = paymentTransactionRepository
            .findByGatewayReference(paymentIntentId)
            .orElse(null);

        if (transaction == null) {
            log.info("PaymentIntent sin transacción asociada (probablemente de Checkout Session): {}",
                paymentIntentId);
            return;
        }

        if (transaction.getStatus() == TransactionStatus.SUCCEEDED) {
            log.info("Transacción ya procesada: {}", transaction.getId());
            return;
        }

        transaction.setStatus(TransactionStatus.SUCCEEDED);
        transaction.setPaidAt(java.time.Instant.now());
        paymentTransactionRepository.save(transaction);

        Invoice invoice = transaction.getInvoice();
        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        Booking booking = invoice.getBooking();
        booking.markAsPaid();

        eventPublisher.publishEvent(new NotificationEvent(
            booking.getUser().getId(),
            NotificationType.INVOICE_PAID,
            NotificationType.INVOICE_PAID.defaultTitle(),
            "La factura %s ha sido pagada exitosamente.".formatted(
                invoice.getInvoiceNumber()),
            "invoice",
            invoice.getId()
        ));

        emailService.sendInvoicePaidEmail(invoiceService.buildInvoiceEmailData(invoice));

        log.info("Pago confirmado vía PaymentIntent: factura {}, transacción {}",
            invoice.getId(), transaction.getId());
    }

    @Transactional
    public PaymentResponse processManualPayment(String invoiceId, String userId,
                                                  ProcessManualPaymentRequest request) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Factura no encontrada: " + invoiceId));

        if (!invoice.isParty(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes acceso a esta factura");
        }

        if (invoice.getStatus() != InvoiceStatus.PENDING
                && invoice.getStatus() != InvoiceStatus.OVERDUE) {
            throw new BadRequestException(
                "La factura no se puede pagar. Estado actual: " + invoice.getStatus());
        }

        if (invoice.getStatus() == InvoiceStatus.OVERDUE) {
            long lateFee = invoiceService.calculateLateFee(invoice);
            invoice.setLateFee(lateFee);
        }

        PaymentTransaction transaction = new PaymentTransaction(
            invoice,
            invoice.getAmountDue(),
            invoice.getCurrency(),
            request.paymentMethod(),
            null,
            TransactionStatus.SUCCEEDED,
            Instant.now(),
            null
        );
        paymentTransactionRepository.save(transaction);

        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        Booking booking = invoice.getBooking();
        booking.markAsPaid();

        eventPublisher.publishEvent(new NotificationEvent(
            booking.getUser().getId(),
            NotificationType.INVOICE_PAID,
            NotificationType.INVOICE_PAID.defaultTitle(),
            "La factura %s ha sido pagada exitosamente.".formatted(
                invoice.getInvoiceNumber()),
            "invoice",
            invoice.getId()
        ));

        emailService.sendInvoicePaidEmail(invoiceService.buildInvoiceEmailData(invoice));

        log.info("Pago manual registrado: factura {}, método {}",
            invoiceId, request.paymentMethod());

        return PaymentResponse.fromEntity(transaction);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getTransactionHistory(String invoiceId, String userId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Factura no encontrada: " + invoiceId));

        if (!invoice.isParty(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "No tienes acceso a esta factura");
        }

        List<PaymentTransaction> transactions =
            paymentTransactionRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);

        return transactions.stream()
            .map(PaymentResponse::fromEntity)
            .toList();
    }
}
