package com.nestorria.server.common.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class HaversineUtilsTest {

    @Test
    void distanceKm_samePoint_returnsZero() {
        double dist = HaversineUtils.distanceKm(4.711, -74.072, 4.711, -74.072);
        assertEquals(0.0, dist, 0.001);
    }

    @Test
    void distanceKm_knownDistance_bogotaMedellin() {
        // Bogotá → Medellin: ~415 km
        double dist = HaversineUtils.distanceKm(4.711, -74.072, 6.244, -75.581);
        assertTrue(dist > 380 && dist < 450,
            "Expected ~415 km, got " + dist);
    }

    @Test
    void distanceKm_differentCoordinates() {
        // Madrid → Barcelona: ~505 km
        double dist = HaversineUtils.distanceKm(40.4168, -3.7038, 41.3874, 2.1686);
        assertTrue(dist > 480 && dist < 530,
            "Expected ~505 km, got " + dist);
    }

    @Test
    void distanceKm_symmetric() {
        double d1 = HaversineUtils.distanceKm(4.711, -74.072, 6.244, -75.581);
        double d2 = HaversineUtils.distanceKm(6.244, -75.581, 4.711, -74.072);
        assertEquals(d1, d2, 0.001);
    }

    @Test
    void distanceKm_nearbyPoints() {
        // Dos puntos a ~1 km de distancia en Bogotá
        double dist = HaversineUtils.distanceKm(4.711, -74.072, 4.712, -74.073);
        assertTrue(dist > 0.1 && dist < 0.2,
            "Expected ~0.15 km, got " + dist);
    }
}
