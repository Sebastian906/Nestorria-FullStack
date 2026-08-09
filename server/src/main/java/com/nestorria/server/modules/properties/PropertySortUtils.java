package com.nestorria.server.modules.properties;

import java.time.Instant;
import java.util.Comparator;

import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;

public final class PropertySortUtils {

    public enum SortField { PRICE, DATE, AREA, RATING }
    public enum SortDirection { ASC, DESC }

    private PropertySortUtils() {}

    public static Comparator<PropertySummaryResponse> getComparator(SortField field, SortDirection direction) {
        Comparator<PropertySummaryResponse> base = switch (field) {
            case PRICE -> (a, b) -> {
                boolean an = a.price().getSale() == null;
                boolean bn = b.price().getSale() == null;
                if (an && bn) return 0;
                if (an) return 1;
                if (bn) return -1;
                return Integer.compare(a.price().getSale(), b.price().getSale());
            };
            case DATE -> (a, b) -> {
                boolean an = a.createdAt() == null;
                boolean bn = b.createdAt() == null;
                if (an && bn) return 0;
                if (an) return 1;
                if (bn) return -1;
                return a.createdAt().compareTo(b.createdAt());
            };
            case AREA -> Comparator.comparingInt(PropertySummaryResponse::area);
            case RATING -> (a, b) -> {
                boolean an = a.averageRating() == null;
                boolean bn = b.averageRating() == null;
                if (an && bn) return 0;
                if (an) return 1;
                if (bn) return -1;
                return Double.compare(a.averageRating(), b.averageRating());
            };
        };
        return direction == SortDirection.DESC ? base.reversed() : base;
    }
}
