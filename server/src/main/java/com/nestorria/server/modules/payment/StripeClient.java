package com.nestorria.server.modules.payment;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class StripeClient {

    public PaymentIntent createPaymentIntent(long amount, String currency,
                                              Map<String, String> metadata) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amount)
            .setCurrency(currency.toLowerCase())
            .putAllMetadata(metadata)
            .build();

        String invoiceId = metadata.get("invoiceId");
        String idempotencyKey = invoiceId != null
            ? "pi_invoice_" + invoiceId
            : UUID.randomUUID().toString();
        RequestOptions options = RequestOptions.builder()
            .setIdempotencyKey(idempotencyKey)
            .build();

        try {
            PaymentIntent paymentIntent = PaymentIntent.create(params, options);
            log.info("PaymentIntent creado: id={}, amount={}, currency={}",
                paymentIntent.getId(), amount, currency);
            return paymentIntent;
        } catch (StripeException e) {
            log.error("Error al crear PaymentIntent: {}", e.getMessage());
            throw new RuntimeException("Error al comunicarse con Stripe: " + e.getMessage(), e);
        }
    }

    public PaymentIntent retrievePaymentIntent(String paymentIntentId) {
        try {
            return PaymentIntent.retrieve(paymentIntentId);
        } catch (StripeException e) {
            log.error("Error al consultar PaymentIntent {}: {}", paymentIntentId, e.getMessage());
            throw new RuntimeException("Error al comunicarse con Stripe: " + e.getMessage(), e);
        }
    }

    public PaymentIntent cancelPaymentIntent(String paymentIntentId) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            return paymentIntent.cancel();
        } catch (StripeException e) {
            log.error("Error al cancelar PaymentIntent {}: {}", paymentIntentId, e.getMessage());
            throw new RuntimeException("Error al comunicarse con Stripe: " + e.getMessage(), e);
        }
    }

    public Event constructWebhookEvent(String payload, String sigHeader,
            String webhookSecret) {
        try {
            return Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (com.stripe.exception.SignatureVerificationException e) {
            throw new com.nestorria.server.common.exception.BadRequestException(
                "Firma de webhook inválida", e);
        } catch (Exception e) {
            throw new com.nestorria.server.common.exception.BadRequestException(
                "Payload de webhook malformado", e);
        }
    }

    public Session createCheckoutSession(long amount, String currency,
                                          Map<String, String> metadata,
                                          String successUrl, String cancelUrl) {
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .addLineItem(SessionCreateParams.LineItem.builder()
                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency(currency.toLowerCase())
                    .setUnitAmount(amount)
                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(metadata.getOrDefault("propertyName", "Nestorria Payment"))
                        .build())
                    .build())
                .setQuantity(1L)
                .build())
            .putAllMetadata(metadata)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .build();

        try {
            String invoiceId = metadata.get("invoiceId");
            String idempotencyKey = invoiceId != null
                ? "cs_invoice_" + invoiceId + "_" + UUID.randomUUID()
                : UUID.randomUUID().toString();
            RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();
            Session session = Session.create(params, options);
            log.info("Checkout Session creada: id={}, url={}", session.getId(), session.getUrl());
            return session;
        } catch (StripeException e) {
            log.error("Error al crear Checkout Session: {}", e.getMessage());
            throw new RuntimeException("Error al comunicarse con Stripe: " + e.getMessage(), e);
        }
    }

    public Session retrieveCheckoutSession(String sessionId) {
        try {
            return Session.retrieve(sessionId);
        } catch (StripeException e) {
            log.warn("No se pudo recuperar Checkout Session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }
}
