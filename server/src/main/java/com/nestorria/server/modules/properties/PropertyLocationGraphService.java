package com.nestorria.server.modules.properties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.algorithm.Graph;
import com.nestorria.server.common.algorithm.HaversineUtils;
import com.nestorria.server.modules.properties.dto.PropertyRouteResponse;
import com.nestorria.server.modules.properties.embeddable.PropertyLocation;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de grafo de proximidad geográfica entre propiedades.
 * Construye un grafo no ponderado/ponderado donde:
 * - Cada nodo es una propiedad (por ID)
 * - Cada arista conecta propiedades dentro de un radio máximo
 * - El peso de cada arista es la distancia Haversine en km
 * Uso principal:
 * - Dijkstra: ruta más corta entre dos propiedades (cadena de propiedades cercanas)
 * - BFS: propiedades alcanzables desde una ubicación
 * - Prim: Minimum Spanning Tree (conecta todas las propiedades con costo mínimo)
 * Complejidad:
 * - buildProximityGraph: O(N²) comparaciones de distancia
 * - findRoute (Dijkstra): O((V + E) log V)
 * - findMinimumSpanningTree (Prim): O(E log V)
 * RENDIMIENTO:
 * - Se cachea con Caffeine (cacheNames = "propertyProximityGraph")
 * - Con más de ~3000 propiedades, considerar indexación espacial (R-tree, KD-tree)
 */
@Service
@Slf4j
public class PropertyLocationGraphService {

    private static final double DEFAULT_MAX_DISTANCE_KM = 10.0;

    private final PropertyRepository propertyRepository;

    public PropertyLocationGraphService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    /**
     * Construye el grafo de proximidad entre propiedades.
     * Conecta propiedades que estén dentro de maxDistanceKm entre sí.
     * Time: O(N²) — cada par de propiedades se compara
     * Space: O(N + E) — adjacency list
     */
    @Cacheable(cacheNames = "propertyProximityGraph", key = "#maxDistanceKm")
    @Transactional(readOnly = true)
    public Graph<String> buildProximityGraph(double maxDistanceKM) {
        List<Property> properties = propertyRepository.findByIsAvailableTrue();
        Graph<String> graph = new Graph<>();

        for (Property p : properties) {
            if (hasValidCoordinates(p)) {
                graph.addVertex(p.getId());
            }
        }

        List<Property> withCoords = properties.stream()
            .filter(this::hasValidCoordinates)
            .toList();

        // O(N²) — aceptable para < 3000 propiedades con coordenadas
        for (int i = 0; i < withCoords.size(); i++) {
            for (int j = i + 1; j < withCoords.size(); j++) {
                Property a = withCoords.get(i);
                Property b = withCoords.get(j);
                double dist = calculateDistance(a, b);
                if (dist <= maxDistanceKM) {
                    graph.addEdge(a.getId(), b.getId());
                }
            }
        }

        log.info("Grafo de proximidad construido: {} vértices, {} aristas (radio={}km)",
            graph.size(), countEdges(graph), maxDistanceKM);

        return graph;
    }

    /**
     * Encuentra la ruta más corta entre dos propiedades usando Dijkstra.
     * El peso de cada arista es la distancia Haversine en km.
     * Retorna: lista de IDs de propiedades en la ruta, y la distancia total.
     * Time: O((V + E) log V)
     */
    @Transactional(readOnly = true)
    public Optional<PropertyRouteResponse> findRoute(String fromPropertyId, String toPropertyId) {
        Graph<String> graph = buildProximityGraph(DEFAULT_MAX_DISTANCE_KM);

        if (!graph.containsVertex(fromPropertyId) || !graph.containsVertex(toPropertyId)) {
            return Optional.empty();
        }

        // Construir mapa de coordenadas para calcular pesos
        Map<String, PropertyLocation> locationMap = buildLocationMap();

        // Dijkstra: peso = distancia Haversine
        Optional<List<String>> path = graph.dijkstra(fromPropertyId, toPropertyId,
            (a, b) -> {
                PropertyLocation locA = locationMap.get(a);
                PropertyLocation locB = locationMap.get(b);
                if (locA == null || locB == null) return Double.POSITIVE_INFINITY;
                return HaversineUtils.distanceKm(
                    locA.getLatitude(), locA.getLongitude(),
                    locB.getLatitude(), locB.getLongitude());
            });

        if (path.isEmpty()) {
            return Optional.empty();
        }

        // Calcular distancia total
        List<String> pathIds = path.get();
        double totalDistance = 0;
        for (int i = 0; i < pathIds.size() - 1; i++) {
            PropertyLocation a = locationMap.get(pathIds.get(i));
            PropertyLocation b = locationMap.get(pathIds.get(i + 1));
            if (a != null && b != null) {
                totalDistance += HaversineUtils.distanceKm(
                    a.getLatitude(), a.getLongitude(),
                    b.getLatitude(), b.getLongitude());
            }
        }

        // Construir respuesta con datos de cada propiedad en la ruta
        List<Property> pathProperties = propertyRepository.findAllById(pathIds);
        Map<String, Property> propertyMap = new HashMap<>();
        for (Property p : pathProperties) {
            propertyMap.put(p.getId(), p);
        }

        List<PropertyRouteResponse.PropertyNode> nodes = pathIds.stream()
            .filter(propertyMap::containsKey)
            .map(id -> {
                Property p = propertyMap.get(id);
                PropertyLocation loc = p.getLocation();
                return new PropertyRouteResponse.PropertyNode(
                    p.getId(), p.getTitle(), p.getCity(),
                    p.getAddress(), p.getPrice(),
                    loc != null ? loc.getLatitude() : null,
                    loc != null ? loc.getLongitude() : null
                );
            })
            .toList();

        return Optional.of(new PropertyRouteResponse(nodes, Math.round(totalDistance * 100.0) / 100.0));
    }

    /**
     * Minimum Spanning Tree: conecta todas las propiedades con la menor distancia total.
     * Útil para visualizar la red óptima de propiedades en una zona.
     * Time: O(E log V)
     */
    @Transactional(readOnly = true)
    public Graph<String> findMinimumSpanningTree() {
        Graph<String> graph = buildProximityGraph(DEFAULT_MAX_DISTANCE_KM);

        if (graph.getVertices().isEmpty()) {
            return new Graph<>();
        }

        Map<String, PropertyLocation> locationMap = buildLocationMap();
        String startVertex = graph.getVertices().iterator().next();

        return graph.primMST(startVertex,
            (a, b) -> {
                PropertyLocation locA = locationMap.get(a);
                PropertyLocation locB = locationMap.get(b);
                if (locA == null || locB == null) return Double.POSITIVE_INFINITY;
                return HaversineUtils.distanceKm(
                    locA.getLatitude(), locA.getLongitude(),
                    locB.getLatitude(), locB.getLongitude());
            });
    }

    // Private helpers
    private boolean hasValidCoordinates(Property p) {
        PropertyLocation loc = p.getLocation();
        return loc != null
            && loc.getLatitude() != null && loc.getLongitude() != null
            && loc.getLatitude() != 0 && loc.getLongitude() != 0;
    }

    private double calculateDistance(Property a, Property b) {
        PropertyLocation locA = a.getLocation();
        PropertyLocation locB = b.getLocation();
        return HaversineUtils.distanceKm(
            locA.getLatitude(), locA.getLongitude(),
            locB.getLatitude(), locB.getLongitude());
    }

    private Map<String, PropertyLocation> buildLocationMap() {
        List<Property> properties = propertyRepository.findByIsAvailableTrue();
        Map<String, PropertyLocation> map = new HashMap<>();
        for (Property p : properties) {
            if (hasValidCoordinates(p)) {
                map.put(p.getId(), p.getLocation());
            }
        }
        return map;
    }

    private int countEdges(Graph<String> graph) {
        int count = 0;
        for (String vertex : graph.getVertices()) {
            count += graph.getNeighbors(vertex).size();
        }
        return count / 2;
    }
}
