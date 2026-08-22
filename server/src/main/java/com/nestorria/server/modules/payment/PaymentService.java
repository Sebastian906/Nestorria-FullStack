package com.nestorria.server.modules.payment;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.event.InvoicePaidEvent;
import com.nestorria.server.common.exception.BadRequestException;
import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.common.mail.EmailService;
import com.nestorria.server.common.outbox.OutboxEventService;
import com.nestorria.server.modules.booking.Booking;
import com.nestorria.server.modules.payment.dto.PaymentIntentResponse;
import com.nestorria.server.modules.payment.dto.PaymentResponse;
import com.nestorria.server.modules.payment.dto.ProcessManualPaymentRequest;
import com.nestorria.server.modules.user.User;
import com.nestorria.server.modules.user.UserRepository;
import com.nestorria.server.modules.user.UserRole;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentService {

    private static final Set<UserRole> MANUAL_PAYMENT_ROLES = Set.of(
        UserRole.AGENCY_OWNER, UserRole.MANAGER, UserRole.ADMINISTRATOR
    );

    private final InvoiceRepository invoiceRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StripeClient stripeClient;
    private final InvoiceService invoiceService;
    private final EmailService emailService;
    private final OutboxEventService outboxEventService;
    private final UserRepository userRepository;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public PaymentService(InvoiceRepository invoiceRepository,
                          PaymentTransactionRepository paymentTransactionRepository,
                          StripeClient stripeClient,
                          InvoiceService invoiceService,
                          EmailService emailService,
                          OutboxEventService outboxEventService,
                          UserRepository userRepository) {
        this.invoiceRepository = invoiceRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.stripeClient = stripeClient;
        this.invoiceService = invoiceService;
        this.emailService = emailService;
        this.outboxEventService = outboxEventService;
        this.userRepository = userRepository;
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

        if (!InvoiceStatus.PAYABLE.contains(invoice.getStatus())) {
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

        // Evitar transacciones duplicadas en doble-click
        Optional<PaymentTransaction> existing = paymentTransactionRepository
            .findByGatewayReference(paymentIntent.getId());
        if (existing.isPresent()) {
            log.info("Transacción ya existe para PaymentIntent {}: {}", paymentIntent.getId(), existing.get().getId());
            return new PaymentIntentResponse(
                invoice.getId(),
                paymentIntent.getClientSecret(),
                invoice.getAmountDue(),
                invoice.getCurrency()
            );
        }

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

    private final Map<String, Consumer<com.stripe.model.Event>> webhookHandlers = Map.of(
        "checkout.session.completed", this::handleCheckoutSessionCompleted,
        "payment_intent.succeeded",   this::handlePaymentIntentSucceeded
    );

    @Transactional
    public void handleStripeWebhook(String payload, String sigHeader) {
        com.stripe.model.Event event = stripeClient.constructWebhookEvent(
            payload, sigHeader, webhookSecret);

        Consumer<com.stripe.model.Event> handler = webhookHandlers.get(event.getType());
        if (handler != null) {
            handler.accept(event);
        } else {
            log.info("Evento de webhook ignorado: {}", event.getType());
        }
    }

    private void handleCheckoutSessionCompleted(com.stripe.model.Event event) {
        com.stripe.model.checkout.Session session =
            (com.stripe.model.checkout.Session) event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow(() -> new BadRequestException(
                    "No se pudo deserializar la Session de Checkout"));

        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || metadata.get("invoiceId") == null) {
            log.warn("Checkout Session sin metadata de factura: {}", session.getId());
            return;
        }

        String invoiceId = metadata.get("invoiceId");

        // Lock pesimista: serializa entregas duplicadas del mismo evento
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
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

        publishInvoicePaid(invoice);

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

        // Lock pesimista: serializa entregas duplicadas del mismo evento
        String invoiceId = transaction.getInvoice().getId();
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Factura no encontrada: " + invoiceId));

        // Re-leer transacción bajo el lock del invoice para estado consistente
        transaction = paymentTransactionRepository
            .findByGatewayReference(paymentIntentId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Transacción no encontrada para PaymentIntent: " + paymentIntentId));

        if (transaction.getStatus() == TransactionStatus.SUCCEEDED) {
            log.info("Transacción ya procesada (post-lock): {}", transaction.getId());
            return;
        }

        transaction.setStatus(TransactionStatus.SUCCEEDED);
        transaction.setPaidAt(java.time.Instant.now());
        paymentTransactionRepository.save(transaction);

        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        Booking booking = invoice.getBooking();
        booking.markAsPaid();

        publishInvoicePaid(invoice);

        log.info("Pago confirmado vía PaymentIntent: factura {}, transacción {}",
            invoice.getId(), transaction.getId());
    }

    @Transactional
    public PaymentResponse processManualPayment(String invoiceId, String userId,
                                                  ProcessManualPaymentRequest request) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Factura no encontrada: " + invoiceId));

        // Solo agency owner o roles privilegiados pueden registrar pagos manuales
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Usuario no encontrado: " + userId));

        boolean isAgencyOwner = invoice.getBooking().getAgency().getOwner().getId().equals(userId);
        boolean hasPrivilege = MANUAL_PAYMENT_ROLES.contains(user.getRole());

        if (!isAgencyOwner && !hasPrivilege) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Solo la agencia o un administrador pueden registrar pagos manuales");
        }

        if (!InvoiceStatus.PAYABLE.contains(invoice.getStatus())) {
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

        publishInvoicePaid(invoice);

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

    private void publishInvoicePaid(Invoice invoice) {
        outboxEventService.publish(
            new InvoicePaidEvent(
                invoice.getId(),
                invoice.getBooking().getUser().getId()),
            "Invoice",
            invoice.getId());
    }
}
