package com.nestorria.server.modules.payment;

import java.time.Instant;

import com.nestorria.server.common.persistence.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "gateway_reference", unique = true)
    private String gatewayReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failure_message")
    private String failureMessage;

    public PaymentTransaction(Invoice invoice, long amount, String currency,
                              PaymentMethod paymentMethod, String gatewayReference,
                              TransactionStatus status, Instant paidAt,
                              String failureMessage) {
        this.invoice = invoice;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.gatewayReference = gatewayReference;
        this.status = status;
        this.paidAt = paidAt;
        this.failureMessage = failureMessage;
    }
}
