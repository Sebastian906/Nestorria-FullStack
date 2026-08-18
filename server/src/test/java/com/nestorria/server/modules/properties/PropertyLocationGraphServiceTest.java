package com.nestorria.server.modules.properties;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nestorria.server.common.algorithm.Graph;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.properties.dto.PropertyRouteResponse;
import com.nestorria.server.modules.properties.embeddable.FacilityDetails;
import com.nestorria.server.modules.properties.embeddable.PriceDetails;
import com.nestorria.server.modules.properties.embeddable.PropertyLocation;

@ExtendWith(MockitoExtension.class)
class PropertyLocationGraphServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    private PropertyLocationGraphService service;

    @BeforeEach
    void setUp() {
        // self = misma instancia: en tests directos no hay proxy Spring ni caché
        service = new PropertyLocationGraphService(propertyRepository, service);
    }

    private Property buildProperty(String id, double lat, double lng) {
        Agency agency = new Agency("Test Agency", "123 Main St",
            "555-0100", "agency@test.com", "Bogota", null);
        PriceDetails price = new PriceDetails(null, 200000);
        FacilityDetails facilities = new FacilityDetails(2, 1, 1);
        PropertyLocation location = new PropertyLocation(lat, lng, "Centro", "110111");

        Property p = new Property(
            agency, "Title " + id, "Description",
            "Bogota", "Colombia", "Address " + id,
            100, PropertyType.APARTMENT, price, facilities,
            List.of(), location
        );
        // El id es GenerationType.UUID y es null en memoria: se fija explícitamente
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void buildProximityGraph_connectsNearbyProperties() {
        Property p1 = buildProperty("1", 4.711, -74.072);  // Bogotá centro
        Property p2 = buildProperty("2", 4.712, -74.073);  // ~0.15 km
        Property p3 = buildProperty("3", 6.244, -75.581);  // Medellín (~415 km)

        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of(p1, p2, p3));

        Graph<String> graph = service.buildProximityGraph(10.0);

        // p1 y p2 están conectadas (cerca), p3 está aislada (lejos)
        assertTrue(graph.containsVertex(p1.getId()));
        assertTrue(graph.containsVertex(p2.getId()));
        assertTrue(graph.containsVertex(p3.getId()));
        assertTrue(graph.getNeighbors(p1.getId()).contains(p2.getId()));
        assertFalse(graph.getNeighbors(p1.getId()).contains(p3.getId()));
    }

    @Test
    void buildProximityGraph_emptyWhenNoProperties() {
        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of());

        Graph<String> graph = service.buildProximityGraph(10.0);
        assertEquals(0, graph.size());
    }

    @Test
    void findRoute_returnsPathBetweenNearbyProperties() {
        Property p1 = buildProperty("1", 4.711, -74.072);
        Property p2 = buildProperty("2", 4.712, -74.073);

        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of(p1, p2));
        when(propertyRepository.findAllById(anyList())).thenReturn(List.of(p1, p2));

        Optional<PropertyRouteResponse> route = service.findRoute(p1.getId(), p2.getId());
        assertTrue(route.isPresent());
        assertEquals(2, route.get().route().size());
        assertTrue(route.get().totalDistanceKm() > 0);
    }

    @Test
    void findRoute_returnsEmptyWhenNoPath() {
        Property p1 = buildProperty("1", 4.711, -74.072);
        Property p2 = buildProperty("2", 6.244, -75.581); // Medellín

        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of(p1, p2));

        Optional<PropertyRouteResponse> route = service.findRoute(p1.getId(), p2.getId());
        assertTrue(route.isEmpty());
    }

    @Test
    void findRoute_returnsEmptyForNonExistentProperty() {
        when(propertyRepository.findByIsAvailableTrue()).thenReturn(List.of());

        Optional<PropertyRouteResponse> route = service.findRoute("nonexistent", "also-nonexistent");
        assertTrue(route.isEmpty());
    }
}
