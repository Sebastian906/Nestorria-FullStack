package com.nestorria.server.modules.booking.dto;

import java.time.LocalDate;

import com.nestorria.server.modules.agency.dto.AgencyResponse;
import com.nestorria.server.modules.booking.Booking;
import com.nestorria.server.modules.booking.BookingStatus;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;

public record BookingResponse(
    String id,
    PropertySummaryResponse property,
    AgencyResponse agency,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    long totalPrice,
    int guests,
    BookingStatus status,
    String paymentMethod,
    boolean isPaid
) {
    public static BookingResponse fromEntity(Booking b) {
        return new BookingResponse(
            b.getId(),
            PropertySummaryResponse.fromEntity(b.getProperty()),
            AgencyResponse.fromEntity(b.getAgency()),
            b.getCheckInDate(),
            b.getCheckOutDate(),
            b.getTotalPrice(),
            b.getGuests(),
            b.getStatus(),
            b.getPaymentMethod(),
            b.isPaid()
        );
    }
}
