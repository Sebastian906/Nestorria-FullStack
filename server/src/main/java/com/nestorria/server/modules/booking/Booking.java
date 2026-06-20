package com.nestorria.server.modules.booking;

import java.time.LocalDate;

import com.nestorria.server.common.persistence.Auditable;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.user.User;

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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @NotNull
    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @NotNull
    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Min(0)
    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Min(1)
    @Column(nullable = false)
    private int guests;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod = "Pay at Check-in";

    @Column(name = "is_paid", nullable = false)
    private boolean isPaid = false;

    public Booking(User user, Property property, Agency agency, LocalDate checkInDate,
                    LocalDate checkOutDate, long totalPrice, int guests) {
        this.user = user;
        this.property = property;
        this.agency = agency;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.totalPrice = totalPrice;
        this.guests = guests;
    }

    public void cancel() {
        if (this.status == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking is already cancelled");
        }
        this.status = BookingStatus.CANCELLED;
    }

    public void confirm() {
        if (this.status == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cannot confirm a cancelled booking");
        }
        if (this.status == BookingStatus.CONFIRMED) {
            return;
        }
        this.status = BookingStatus.CONFIRMED;
    }

    public void markAsPaid() {
        this.isPaid = true;
    }

}
