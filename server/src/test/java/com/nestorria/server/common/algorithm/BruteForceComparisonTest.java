package com.nestorria.server.common.algorithm;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.booking.Booking;
import com.nestorria.server.modules.booking.BookingStatus;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyType;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.properties.embeddable.PropertyLocation;
import com.nestorria.server.modules.user.User;

class BruteForceComparisonTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_5 = LocalDate.of(2026, 1, 5);

    // Construye un Booking real con las fechas y estado dados.
    private Booking booking(LocalDate checkIn, LocalDate checkOut, BookingStatus status) {
        User user = new User("u1", "User", "u@example.com", "img");
        Agency agency = new Agency("Agency", "Addr", "Contact", "a@example.com", "City", user);
        Property property = new Property(agency, "Title", "Desc", "City", "Country", "Addr",
            100, PropertyType.APARTMENT, new PriceDetails(), new FacilityDetails(),
            List.of(), new PropertyLocation(null, null, null, null));

        Booking b = new Booking(user, property, agency, checkIn, checkOut, 1000L, 2);
        if (status == BookingStatus.CONFIRMED) {
            b.confirm();
        } else if (status == BookingStatus.CANCELLED) {
            b.cancel();
        }
        return b;
    }

    @Test
    void overlappingConfirmedBooking_detected() {
        Booking existing = booking(JAN_1, JAN_5, BookingStatus.CONFIRMED);
        assertTrue(BruteForceComparison.existsOverlapBruteForce(
            List.of(existing), LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 7)));
    }

    @Test
    void nonOverlappingBooking_notDetected() {
        Booking existing = booking(JAN_1, JAN_5, BookingStatus.CONFIRMED);
        assertFalse(BruteForceComparison.existsOverlapBruteForce(
            List.of(existing), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9)));
    }

    @Test
    void adjacentDates_noOverlap() {
        // Semántica half-open del repositorio: checkOut == checkIn del siguiente → no solapa
        Booking existing = booking(JAN_1, JAN_5, BookingStatus.CONFIRMED);
        assertFalse(BruteForceComparison.existsOverlapBruteForce(
            List.of(existing), JAN_5, LocalDate.of(2026, 1, 8)));
    }

    @Test
    void cancelledBooking_ignored() {
        Booking cancelled = booking(JAN_1, JAN_5, BookingStatus.CANCELLED);
        assertFalse(BruteForceComparison.existsOverlapBruteForce(
            List.of(cancelled), LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 7)));
    }

    @Test
    void pendingBooking_countsAsOverlap() {
        // Misma semántica que existsOverlappingBooking: todo status <> CANCELLED bloquea
        Booking pending = booking(JAN_1, JAN_5, BookingStatus.PENDING);
        assertTrue(BruteForceComparison.existsOverlapBruteForce(
            List.of(pending), LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 7)));
    }

    @Test
    void emptyList_returnsFalse() {
        assertFalse(BruteForceComparison.existsOverlapBruteForce(
            List.of(), JAN_1, JAN_5));
    }
}
