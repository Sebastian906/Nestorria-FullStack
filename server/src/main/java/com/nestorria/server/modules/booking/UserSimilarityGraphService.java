package com.nestorria.server.modules.booking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.algorithm.Graph;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de grafo de similitud entre usuarios basado en patrones de reserva.
 * Construye un grafo bipartito User ↔ Property:
 * - Cada nodo es un User o una Property
 * - Una arista existe cuando el usuario reservó la propiedad
 * A partir de este grafo se pueden encontrar:
 * - Usuarios con patrones de reserva similares (comparten propiedades)
 * - Propiedades recomendadas vía collaborative filtering
 * - Comunidades de usuarios (DFS connected components)
 * Complejidad:
 * - buildBipartiteGraph: O(U × P) donde U = usuarios, P = propiedades
 * - findSimilarUsers: O(V + E) BFS desde el usuario
 * - getCollaborativeRecommendations: O(U × P) en peor caso
 * NOTA: Este servicio reemplaza la lógica stub de collaborative filtering
 * en PropertyRecommendationService.
 */
@Service
@Slf4j
public class UserSimilarityGraphService {

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final UserSimilarityGraphService self;

    public UserSimilarityGraphService(
            BookingRepository bookingRepository,
            PropertyRepository propertyRepository,
            @Lazy UserSimilarityGraphService self) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        // En Spring llega el proxy @Lazy; en tests directos se usa this
        this.self = self != null ? self : this;
    }

    /**
     * Grafo bipartito + conjunto de vértices que son usuarios.
     * Se cachea junto con el grafo para filtrar comunidades sin re-scanear reservas.
     */
    public record BipartiteGraphData(Graph<String> graph, Set<String> userVertices) {}

    /**
     * Construye el grafo bipartito User ↔ Property (cacheado).
     * Cada usuario está conectado con las propiedades que reservó (no canceladas).
     * Time: O(U × P) — scan de todas las reservas
     * Space: O(U + P + E) — grafo bipartito
     */
    @Cacheable(cacheNames = "userPropertyGraph", key = "'bipartite'")
    @Transactional(readOnly = true)
    public BipartiteGraphData buildBipartiteGraph() {
        Graph<String> graph = new Graph<>();
        Set<String> userVertices = new HashSet<>();

        List<Property> allProperties = propertyRepository.findByIsAvailableTrue();
        Set<String> allPropertyIds = allProperties.stream()
            .map(Property::getId)
            .collect(Collectors.toSet());

        // Obtener todas las reservas confirmadas y extraer user→property
        List<Booking> allBookings = getAllConfirmedBookings();

        for (Booking booking : allBookings) {
            String userId = booking.getUser().getId();
            String propertyId = booking.getProperty().getId();

            graph.addVertex(userId);
            userVertices.add(userId);
            if (allPropertyIds.contains(propertyId)) {
                graph.addVertex(propertyId);
                graph.addEdge(userId, propertyId);
            }
        }

        log.info("Grafo bipartito construido: {} vértices, {} aristas",
            graph.size(), countEdges(graph));

        return new BipartiteGraphData(graph, userVertices);
    }

    /**
     * Encuentra usuarios similares a un usuario dado.
     * "Similar" = comparten al menos una propiedad reservada.
     * BFS en el grafo bipartito: User → Property → Other Users.
     * Time: O(V + E) BFS
     * Space: O(V)
     */
    @Transactional(readOnly = true)
    public List<String> findSimilarUsers(String userId, int limit) {
        Graph<String> graph = self.buildBipartiteGraph().graph();

        if (!graph.containsVertex(userId)) {
            return List.of();
        }

        // BFS de 2 saltos: User → Property → User
        Set<String> visited = new HashSet<>();
        List<String> similarUsers = new ArrayList<>();

        // Nivel 1: propiedades del usuario
        Set<String> userProperties = graph.getNeighbors(userId);
        visited.add(userId);
        visited.addAll(userProperties);

        // Nivel 2: usuarios que reservaron esas propiedades.
        // El grafo bipartito garantiza que los vecinos de una propiedad son usuarios.
        for (String propertyId : userProperties) {
            for (String neighbor : graph.getNeighbors(propertyId)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    similarUsers.add(neighbor);
                    if (similarUsers.size() >= limit) break;
                }
            }
            if (similarUsers.size() >= limit) break;
        }

        return similarUsers;
    }

    /**
     * Collaborative filtering: propiedades reservadas por usuarios similares
     * pero NO reservadas por el usuario objetivo.
     * Time: O(U × P) peor caso
     * Space: O(P)
     */
    @Transactional(readOnly = true)
    public List<PropertySummaryResponse> getCollaborativeRecommendations(
            String userId, int limit) {

        // 1. Grafo bipartito (cacheado): aristas solo entre usuarios y propiedades
        // reservadas confirmadas, por lo que no hay que re-filtrar por estado.
        Graph<String> graph = self.buildBipartiteGraph().graph();

        // Propiedades reservadas por el usuario objetivo
        Set<String> ownPropertyIds = graph.getNeighbors(userId);

        // 2. Encontrar usuarios similares
        List<String> similarUserIds = findSimilarUsers(userId, 20);

        if (similarUserIds.isEmpty()) {
            return List.of();
        }

        // 3. Propiedades de usuarios similares, excluyendo las propias.
        // Los vecinos de un usuario en el grafo bipartito son sus reservas confirmadas.
        Map<String, Long> propertyScore = new HashMap<>();

        for (String similarUserId : similarUserIds) {
            for (String propertyId : graph.getNeighbors(similarUserId)) {
                if (!ownPropertyIds.contains(propertyId)) {
                    propertyScore.merge(propertyId, 1L, Long::sum);
                }
            }
        }

        // 4. Ordenar por frecuencia (más reservada = mejor recomendación)
        List<String> recommendedIds = propertyScore.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .toList();

        if (recommendedIds.isEmpty()) {
            return List.of();
        }

        // 5. Obtener propiedades y convertir a DTOs
        List<Property> properties = propertyRepository.findAllById(recommendedIds);
        Map<String, Property> propertyMap = new HashMap<>();
        for (Property p : properties) {
            propertyMap.put(p.getId(), p);
        }

        return recommendedIds.stream()
            .filter(propertyMap::containsKey)
            .map(id -> PropertySummaryResponse.fromEntity(propertyMap.get(id)))
            .toList();
    }

    /**
     * Encuentra comunidades de usuarios (componentes conectados del grafo).
     * Cada comunidad es un grupo de usuarios que están conectados
     * a través de propiedades compartidas. Los IDs de propiedades se
     * descartan de la respuesta.
     * Time: O(V + E)
     */
    @Transactional(readOnly = true)
    public List<Set<String>> findUserCommunities() {
        BipartiteGraphData data = self.buildBipartiteGraph();

        // Intersección de cada componente con el conjunto de usuarios
        return data.graph().connectedComponents().stream()
            .map(component -> {
                component.retainAll(data.userVertices());
                return component;
            })
            .filter(component -> !component.isEmpty())
            .toList();
    }

    // Private helpers
    private List<Booking> getAllConfirmedBookings() {
        return bookingRepository.findAllConfirmed();
    }

    private int countEdges(Graph<String> graph) {
        int count = 0;
        for (String vertex : graph.getVertices()) {
            count += graph.getNeighbors(vertex).size();
        }
        return count / 2;
    }
}
