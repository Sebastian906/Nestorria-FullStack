package com.nestorria.server.modules.payment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.nestorria.server.common.persistence.Auditable;
import com.nestorria.server.modules.booking.Booking;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "invoices",
    uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @NotNull
    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @NotNull
    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @NotNull
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private long subtotal;

    @Column(nullable = false)
    private long tax;

    @Column(nullable = false)
    private long total;

    @Column(nullable = false)
    private String currency = "USD";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.PENDING;

    @Column(name = "late_fee", nullable = false)
    private long lateFee = 0;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentTransaction> paymentTransactions = new ArrayList<>();

    public Invoice(Booking booking, String invoiceNumber, LocalDate issueDate,
                   LocalDate dueDate, long subtotal, long tax, long total,
                   String currency) {
        this.booking = booking;
        this.invoiceNumber = invoiceNumber;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.subtotal = subtotal;
        this.tax = tax;
        this.total = total;
        this.currency = currency;
        this.status = InvoiceStatus.PENDING;
        this.lateFee = 0;
    }

    public long getAmountDue() {
        return total + lateFee;
    }

    public boolean isParty(String userId) {
        return booking.getUser().getId().equals(userId)
            || booking.getAgency().getOwner().getId().equals(userId);
    }
}
