package com.nestorria.server.common.mail;

import java.time.LocalDate;

public record BookingEmailData(
    String bookingId,
    String userEmail,
    String agencyName,
    String propertyAddress,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    long totalPrice,
    long nights,
    int guests
) {}
