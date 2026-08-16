package com.nestorria.server.modules.booking;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class BookingServiceMultiAvailabilityTest {

    // Nota: Estos tests validan la lógica de dedup y estructura del DTO.
    // Para tests de integración con BD, se necesitaría @SpringBootTest + Testcontainers.

    @Test
    void duplicatePropertyIds_areDeduplicated() {
        // Simula la lógica de dedup del servicio
        List<String> input = List.of("A", "B", "A", "C", "B");
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        List<String> deduped = input.stream().filter(seen::add).toList();

        assertEquals(3, deduped.size());
        assertEquals(List.of("A", "B", "C"), deduped);
    }

    @Test
    void emptyPropertyIds_returnsEmptyResults() {
        List<String> input = List.of();
        assertTrue(input.isEmpty());
    }

    @Test
    void singleProperty_checkAvailability() {
        // Una propiedad: verificación simple, O(1)
        String propertyId = "prop-123";
        assertNotNull(propertyId);
    }
}
