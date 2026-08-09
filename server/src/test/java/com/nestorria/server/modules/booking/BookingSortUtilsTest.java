package com.nestorria.server.modules.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nestorria.server.modules.booking.BookingSortUtils.SortDirection;
import com.nestorria.server.modules.booking.BookingSortUtils.SortField;
import com.nestorria.server.modules.booking.dto.BookingResponse;

import static org.junit.jupiter.api.Assertions.*;

class BookingSortUtilsTest {

    private BookingResponse bk(String id, LocalDate checkIn, long price, Instant createdAt) {
        return new BookingResponse(
            id, null, null,
            checkIn, checkIn.plusDays(1),
            price, 2,
            BookingStatus.CONFIRMED,
            "Pay at Check-in",
            false,
            createdAt
        );
    }

    @Test
    void checkInAsc() {
        var list = new ArrayList<>(List.of(
            bk("a", LocalDate.of(2026, 3, 10), 500, Instant.now()),
            bk("b", LocalDate.of(2026, 1, 5), 300, Instant.now())
        ));
        list.sort(BookingSortUtils.getComparator(SortField.CHECK_IN, SortDirection.ASC));
        assertEquals("b", list.get(0).id());
    }

    @Test
    void checkOutDesc() {
        var list = new ArrayList<>(List.of(
            bk("a", LocalDate.of(2026, 1, 1), 500, Instant.now()),
            bk("b", LocalDate.of(2026, 3, 1), 500, Instant.now())
        ));
        list.sort(BookingSortUtils.getComparator(SortField.CHECK_OUT, SortDirection.DESC));
        assertEquals("b", list.get(0).id());
    }

    @Test
    void totalPriceDesc() {
        var list = new ArrayList<>(List.of(
            bk("a", LocalDate.now(), 500, Instant.now()),
            bk("b", LocalDate.now(), 1000, Instant.now())
        ));
        list.sort(BookingSortUtils.getComparator(SortField.TOTAL_PRICE, SortDirection.DESC));
        assertEquals("b", list.get(0).id());
    }

    @Test
    void totalPriceAsc() {
        var list = new ArrayList<>(List.of(
            bk("a", LocalDate.now(), 1000, Instant.now()),
            bk("b", LocalDate.now(), 500, Instant.now())
        ));
        list.sort(BookingSortUtils.getComparator(SortField.TOTAL_PRICE, SortDirection.ASC));
        assertEquals("b", list.get(0).id());
    }

    @Test
    void createdAtDesc() {
        Instant now = Instant.now();
        var list = new ArrayList<>(List.of(
            bk("a", LocalDate.now(), 500, now.minusSeconds(100)),
            bk("b", LocalDate.now(), 500, now)
        ));
        list.sort(BookingSortUtils.getComparator(SortField.CREATED_AT, SortDirection.DESC));
        assertEquals("b", list.get(0).id());
    }

    @Test
    void createdAtAsc() {
        Instant now = Instant.now();
        var list = new ArrayList<>(List.of(
            bk("a", LocalDate.now(), 500, now),
            bk("b", LocalDate.now(), 500, now.minusSeconds(100))
        ));
        list.sort(BookingSortUtils.getComparator(SortField.CREATED_AT, SortDirection.ASC));
        assertEquals("b", list.get(0).id());
    }

    @Test
    void emptyList() {
        var list = new ArrayList<BookingResponse>();
        list.sort(BookingSortUtils.getComparator(SortField.CHECK_IN, SortDirection.ASC));
        assertTrue(list.isEmpty());
    }
}
