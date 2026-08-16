package com.nestorria.server.modules.booking;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class BookingSchedulerTest {

    @Test
    void selectNonOverlapping_selectsMaximum() {
        Booking b1 = createBooking(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));
        Booking b2 = createBooking(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 7));
        Booking b3 = createBooking(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9));
        Booking b4 = createBooking(LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 12));

        List<Booking> result = BookingScheduler.selectNonOverlapping(
            List.of(b1, b2, b3, b4));

        // b1 (ends Jan 5), b3 (starts Jan 5, ends Jan 9), b4 (starts Jan 8... overlaps with b3)
        // Actually: b1 ends Jan 5, b3 starts Jan 5 → non-overlapping (>=). b3 ends Jan 9, b4 starts Jan 8 → overlapping
        // So: b1, b3
        assertEquals(2, result.size());
        assertEquals(b1, result.get(0));
        assertEquals(b3, result.get(1));
    }

    @Test
    void selectNonOverlapping_emptyList() {
        assertTrue(BookingScheduler.selectNonOverlapping(List.of()).isEmpty());
    }

    @Test
    void selectNonOverlapping_nullList() {
        assertTrue(BookingScheduler.selectNonOverlapping(null).isEmpty());
    }

    @Test
    void selectNonOverlapping_singleBooking() {
        Booking b = createBooking(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));
        assertEquals(1, BookingScheduler.selectNonOverlapping(List.of(b)).size());
    }

    @Test
    void selectNonOverlapping_allOverlap() {
        Booking b1 = createBooking(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));
        Booking b2 = createBooking(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 8));
        Booking b3 = createBooking(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 7));

        assertEquals(1, BookingScheduler.selectNonOverlapping(
            List.of(b1, b2, b3)).size());
    }

    @Test
    void selectNonOverlapping_noOverlaps() {
        Booking b1 = createBooking(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));
        Booking b2 = createBooking(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 6));
        Booking b3 = createBooking(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 9));

        assertEquals(3, BookingScheduler.selectNonOverlapping(
            List.of(b1, b2, b3)).size());
    }

    @Test
    void selectNonOverlapping_adjacentDatesAreNonOverlapping() {
        // checkOut of b1 == checkIn of b2 → non-overlapping (>=)
        Booking b1 = createBooking(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));
        Booking b2 = createBooking(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9));

        assertEquals(2, BookingScheduler.selectNonOverlapping(
            List.of(b1, b2)).size());
    }

    private Booking createBooking(LocalDate checkIn, LocalDate checkOut) {
        Booking booking = new Booking(null, null, null, checkIn, checkOut, 10000, 2);
        return booking;
    }
}
