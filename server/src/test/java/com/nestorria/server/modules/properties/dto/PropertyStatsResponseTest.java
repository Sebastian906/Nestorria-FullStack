package com.nestorria.server.modules.properties.dto;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PropertyStatsResponseTest {

    // ==================== PriceStatistics TESTS ====================

    @Test
    void priceStatistics_emptyList() {
        var stats = PropertyStatsResponse.PriceStatistics.fromPrices(List.of());
        
        assertNull(stats.min());
        assertNull(stats.max());
        assertEquals(0.0, stats.average());
        assertFalse(stats.median().isPresent());
        assertEquals(0, stats.count());
    }

    @Test
    void priceStatistics_singlePrice() {
        var stats = PropertyStatsResponse.PriceStatistics.fromPrices(List.of(100));
        
        assertEquals(100, stats.min());
        assertEquals(100, stats.max());
        assertEquals(100.0, stats.average());
        assertTrue(stats.median().isPresent());
        assertEquals(100.0, stats.median().getAsDouble());
        assertEquals(1, stats.count());
    }

    @Test
    void priceStatistics_oddNumberOfElements() {
        var stats = PropertyStatsResponse.PriceStatistics.fromPrices(
            List.of(100, 200, 300, 400, 500));
        
        assertEquals(100, stats.min());
        assertEquals(500, stats.max());
        assertEquals(300.0, stats.average());
        assertTrue(stats.median().isPresent());
        assertEquals(300.0, stats.median().getAsDouble());
        assertEquals(5, stats.count());
    }

    @Test
    void priceStatistics_evenNumberOfElements() {
        var stats = PropertyStatsResponse.PriceStatistics.fromPrices(
            List.of(100, 200, 300, 400));
        
        assertEquals(100, stats.min());
        assertEquals(400, stats.max());
        assertEquals(250.0, stats.average());
        assertTrue(stats.median().isPresent());
        assertEquals(250.0, stats.median().getAsDouble());
        assertEquals(4, stats.count());
    }

    @Test
    void priceStatistics_withNulls() {
        var stats = PropertyStatsResponse.PriceStatistics.fromPrices(
            List.of(100, null, 300, null, 500));
        
        assertEquals(100, stats.min());
        assertEquals(500, stats.max());
        assertEquals(300.0, stats.average());
        assertTrue(stats.median().isPresent());
        assertEquals(300.0, stats.median().getAsDouble());
        assertEquals(3, stats.count()); // Solo 3 precios válidos
    }

    @Test
    void priceStatistics_allNulls() {
        var stats = PropertyStatsResponse.PriceStatistics.fromPrices(
            List.of(null, null, null));
        
        assertNull(stats.min());
        assertNull(stats.max());
        assertEquals(0.0, stats.average());
        assertFalse(stats.median().isPresent());
        assertEquals(0, stats.count());
    }

    @Test
    void priceStatistics_unsortedInput() {
        var stats = PropertyStatsResponse.PriceStatistics.fromPrices(
            List.of(500, 100, 300, 200, 400));
        
        assertEquals(100, stats.min());
        assertEquals(500, stats.max());
        assertEquals(300.0, stats.average());
        assertTrue(stats.median().isPresent());
        assertEquals(300.0, stats.median().getAsDouble());
    }

    @Test
    void priceStatistics_duplicatePrices() {
        var stats = PropertyStatsResponse.PriceStatistics.fromPrices(
            List.of(200, 200, 200, 200));
        
        assertEquals(200, stats.min());
        assertEquals(200, stats.max());
        assertEquals(200.0, stats.average());
        assertTrue(stats.median().isPresent());
        assertEquals(200.0, stats.median().getAsDouble());
        assertEquals(4, stats.count());
    }

    @Test
    void priceStatistics_negativePrices() {
        var stats = PropertyStatsResponse.PriceStatistics.fromPrices(
            List.of(-100, 0, 100, 200));
        
        assertEquals(-100, stats.min());
        assertEquals(200, stats.max());
        assertEquals(50.0, stats.average());
        assertTrue(stats.median().isPresent());
        assertEquals(50.0, stats.median().getAsDouble());
    }
}
