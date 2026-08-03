package com.nestorria.server.modules.payment;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.modules.payment.dto.InvoiceResponse;
import com.nestorria.server.modules.payment.dto.PaymentIntentResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Invoices", description = "Gestión de facturas y pagos")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;

    public InvoiceController(InvoiceService invoiceService,
                             PaymentService paymentService) {
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
    }

    @Operation(summary = "Obtener las facturas del usuario autenticado")
    @GetMapping("/me")
    public ResponseEntity<List<InvoiceResponse>> getMyInvoices(
            @AuthenticationPrincipal Jwt jwt) {
        List<InvoiceResponse> invoices = invoiceService.getUserInvoices(jwt.getSubject());
        return ResponseEntity.ok(invoices);
    }

    @Operation(summary = "Obtener una factura completa con transacciones de pago")
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        InvoiceResponse invoice = invoiceService.getInvoice(id, jwt.getSubject());
        return ResponseEntity.ok(invoice);
    }

    @Operation(summary = "Iniciar pago de una factura con Stripe")
    @PostMapping("/{id}/pay")
    public ResponseEntity<PaymentIntentResponse> payInvoice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        PaymentIntentResponse response = paymentService.createPaymentIntent(id, jwt.getSubject());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cancelar una factura pendiente")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<InvoiceResponse> cancelInvoice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        InvoiceResponse response = invoiceService.cancelInvoice(id, jwt.getSubject());
        return ResponseEntity.ok(response);
    }
}
