package com.nestorria.server.modules.properties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nestorria.server.modules.properties.PropertySortUtils.SortDirection;
import com.nestorria.server.modules.properties.PropertySortUtils.SortField;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;

import static org.junit.jupiter.api.Assertions.*;

class PropertySortUtilsTest {

    private PropertySummaryResponse prop(String id, Integer salePrice, int area, Instant createdAt) {
        return new PropertySummaryResponse(
            id, "Title", "Desc", "City", "Country", "Addr", area,
            PropertyType.HOUSE,
            new PriceDetails(null, salePrice),
            null, List.of(), List.of(),
            true, null, null,
            null, 0,
            createdAt
        );
    }

    private PropertySummaryResponse propWithRating(String id, Double rating) {
        return new PropertySummaryResponse(
            id, "Title", "Desc", "City", "Country", "Addr", 50,
            PropertyType.HOUSE,
            new PriceDetails(null, 100),
            null, List.of(), List.of(),
            true, null, null,
            rating, 0,
            Instant.now()
        );
    }

    @Test
    void priceAsc() {
        var list = new ArrayList<>(List.of(
            prop("a", 100, 50, Instant.now()),
            prop("b", 50, 50, Instant.now())
        ));
        list.sort(PropertySortUtils.getComparator(SortField.PRICE, SortDirection.ASC));
        assertEquals("b", list.get(0).id());
    }

    @Test
    void priceDesc() {
        var list = new ArrayList<>(List.of(
            prop("a", 100, 50, Instant.now()),
            prop("b", 50, 50, Instant.now())
        ));
        list.sort(PropertySortUtils.getComparator(SortField.PRICE, SortDirection.DESC));
        assertEquals("a", list.get(0).id());
    }

    @Test
    void priceNullSortedLastAsc() {
        var list = new ArrayList<>(List.of(
            prop("a", null, 50, Instant.now()),
            prop("b", 50, 50, Instant.now())
        ));
        list.sort(PropertySortUtils.getComparator(SortField.PRICE, SortDirection.ASC));
        assertEquals("b", list.get(0).id());
        assertEquals("a", list.get(1).id());
    }

    @Test
    void priceNullSortedFirstDesc() {
        var list = new ArrayList<>(List.of(
            prop("a", null, 50, Instant.now()),
            prop("b", 50, 50, Instant.now())
        ));
        list.sort(PropertySortUtils.getComparator(SortField.PRICE, SortDirection.DESC));
        assertEquals("a", list.get(0).id());
        assertEquals("b", list.get(1).id());
    }

    @Test
    void areaAsc() {
        var list = new ArrayList<>(List.of(
            prop("a", 100, 200, Instant.now()),
            prop("b", 100, 50, Instant.now())
        ));
        list.sort(PropertySortUtils.getComparator(SortField.AREA, SortDirection.ASC));
        assertEquals("b", list.get(0).id());
    }

    @Test
    void areaDesc() {
        var list = new ArrayList<>(List.of(
            prop("a", 100, 50, Instant.now()),
            prop("b", 100, 200, Instant.now())
        ));
        list.sort(PropertySortUtils.getComparator(SortField.AREA, SortDirection.DESC));
        assertEquals("b", list.get(0).id());
    }

    @Test
    void dateDesc() {
        Instant now = Instant.now();
        var list = new ArrayList<>(List.of(
            prop("a", 100, 50, now.minusSeconds(100)),
            prop("b", 100, 50, now)
        ));
        list.sort(PropertySortUtils.getComparator(SortField.DATE, SortDirection.DESC));
        assertEquals("b", list.get(0).id());
    }

    @Test
    void dateAsc() {
        Instant now = Instant.now();
        var list = new ArrayList<>(List.of(
            prop("a", 100, 50, now),
            prop("b", 100, 50, now.minusSeconds(100))
        ));
        list.sort(PropertySortUtils.getComparator(SortField.DATE, SortDirection.ASC));
        assertEquals("b", list.get(0).id());
    }

    @Test
    void dateNullSafe() {
        var list = new ArrayList<>(List.of(
            prop("a", 100, 50, null),
            prop("b", 100, 50, Instant.now())
        ));
        list.sort(PropertySortUtils.getComparator(SortField.DATE, SortDirection.ASC));
        assertEquals("b", list.get(0).id());
        assertEquals("a", list.get(1).id());
    }

    @Test
    void ratingAsc() {
        var list = new ArrayList<>(List.of(
            propWithRating("a", 4.5),
            propWithRating("b", 3.0)
        ));
        list.sort(PropertySortUtils.getComparator(SortField.RATING, SortDirection.ASC));
        assertEquals("b", list.get(0).id());
    }

    @Test
    void ratingDesc() {
        var list = new ArrayList<>(List.of(
            propWithRating("a", 4.5),
            propWithRating("b", 3.0)
        ));
        list.sort(PropertySortUtils.getComparator(SortField.RATING, SortDirection.DESC));
        assertEquals("a", list.get(0).id());
    }

    @Test
    void ratingNullSortedLastAsc() {
        var list = new ArrayList<>(List.of(
            propWithRating("a", null),
            propWithRating("b", 4.0)
        ));
        list.sort(PropertySortUtils.getComparator(SortField.RATING, SortDirection.ASC));
        assertEquals("b", list.get(0).id());
        assertEquals("a", list.get(1).id());
    }

    @Test
    void emptyList() {
        var list = new ArrayList<PropertySummaryResponse>();
        list.sort(PropertySortUtils.getComparator(SortField.PRICE, SortDirection.ASC));
        assertTrue(list.isEmpty());
    }
}
