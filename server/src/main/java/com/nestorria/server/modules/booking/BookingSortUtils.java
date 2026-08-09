package com.nestorria.server.modules.booking;

import java.time.Instant;
import java.util.Comparator;

import com.nestorria.server.modules.booking.dto.BookingResponse;

public final class BookingSortUtils {

    public enum SortField { CHECK_IN, CHECK_OUT, TOTAL_PRICE, CREATED_AT }
    public enum SortDirection { ASC, DESC }

    private BookingSortUtils() {}

    public static Comparator<BookingResponse> getComparator(SortField field, SortDirection direction) {
        Comparator<BookingResponse> base = switch (field) {
            case CHECK_IN -> Comparator.comparing(BookingResponse::checkInDate);
            case CHECK_OUT -> Comparator.comparing(BookingResponse::checkOutDate);
            case TOTAL_PRICE -> Comparator.comparingLong(BookingResponse::totalPrice);
            case CREATED_AT -> (a, b) -> {
                Instant da = a.createdAt() != null ? a.createdAt() : Instant.MIN;
                Instant db = b.createdAt() != null ? b.createdAt() : Instant.MIN;
                return da.compareTo(db);
            };
        };
        return direction == SortDirection.DESC ? base.reversed() : base;
    }
}
