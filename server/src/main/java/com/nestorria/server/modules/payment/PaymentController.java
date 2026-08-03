package com.nestorria.server.modules.payment;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.modules.payment.dto.PaymentResponse;
import com.nestorria.server.modules.payment.dto.ProcessManualPaymentRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Gestión de pagos y webhooks")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    private static final int MAX_WEBHOOK_PAYLOAD_BYTES = 256 * 1024;

    @Operation(summary = "Webhook de Stripe para confirmación de pagos (público)")
    @PostMapping("/stripe/webhook")
    public ResponseEntity<Void> handleStripeWebhook(HttpServletRequest request) {
        String sigHeader = request.getHeader("Stripe-Signature");
        if (sigHeader == null || sigHeader.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getContentLengthLong() > MAX_WEBHOOK_PAYLOAD_BYTES) {
            return ResponseEntity.badRequest().build();
        }
        String payload;
        try {
            payload = new String(
                request.getInputStream().readNBytes(MAX_WEBHOOK_PAYLOAD_BYTES),
                java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
        paymentService.handleStripeWebhook(payload, sigHeader);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Registrar un pago manual (transferencia o efectivo)")
    @PostMapping("/invoices/{invoiceId}/manual")
    public ResponseEntity<PaymentResponse> processManualPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String invoiceId,
            @Valid @RequestBody ProcessManualPaymentRequest request) {
        PaymentResponse response = paymentService.processManualPayment(
            invoiceId, jwt.getSubject(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Obtener historial de transacciones de una factura")
    @GetMapping("/invoices/{invoiceId}/transactions")
    public ResponseEntity<List<PaymentResponse>> getTransactionHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String invoiceId) {
        List<PaymentResponse> transactions = paymentService.getTransactionHistory(
            invoiceId, jwt.getSubject());
        return ResponseEntity.ok(transactions);
    }
}
