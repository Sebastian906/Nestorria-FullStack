package com.nestorria.server.modules.booking;

import java.time.Instant;
import java.util.Comparator;

import com.nestorria.server.modules.booking.dto.BookingResponse;

public final class BookingSortUtils {

    public enum SortField { CHECK_IN, CHECK_OUT, TOTAL_PRICE, STATUS, CREATED_AT }
    public enum SortDirection { ASC, DESC }

    private BookingSortUtils() {}

    public static Comparator<BookingResponse> getComparator(SortField field, SortDirection direction) {
        Comparator<BookingResponse> base = switch (field) {
            case CHECK_IN -> Comparator.comparing(BookingResponse::checkInDate);
            case CHECK_OUT -> Comparator.comparing(BookingResponse::checkOutDate);
            case TOTAL_PRICE -> Comparator.comparingLong(BookingResponse::totalPrice);
            case STATUS -> Comparator.comparing(BookingResponse::status);
            case CREATED_AT -> (a, b) -> {
                boolean an = a.createdAt() == null;
                boolean bn = b.createdAt() == null;
                if (an && bn) return 0;
                if (an) return 1;
                if (bn) return -1;
                return a.createdAt().compareTo(b.createdAt());
            };
        };
        return direction == SortDirection.DESC ? base.reversed() : base;
    }
}
