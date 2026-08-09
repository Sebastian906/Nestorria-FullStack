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
                int va = a.price().getSale() != null ? a.price().getSale() : Integer.MAX_VALUE;
                int vb = b.price().getSale() != null ? b.price().getSale() : Integer.MAX_VALUE;
                return Integer.compare(va, vb);
            };
            case DATE -> (a, b) -> {
                Instant da = a.createdAt() != null ? a.createdAt() : Instant.MAX;
                Instant db = b.createdAt() != null ? b.createdAt() : Instant.MAX;
                return da.compareTo(db);
            };
            case AREA -> Comparator.comparingInt(PropertySummaryResponse::area);
            case RATING -> (a, b) -> {
                Double ra = a.averageRating() != null ? a.averageRating() : Double.NEGATIVE_INFINITY;
                Double rb = b.averageRating() != null ? b.averageRating() : Double.NEGATIVE_INFINITY;
                return Double.compare(ra, rb);
            };
        };
        return direction == SortDirection.DESC ? base.reversed() : base;
    }
}
