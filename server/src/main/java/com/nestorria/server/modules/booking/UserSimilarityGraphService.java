package com.nestorria.server.modules.booking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.algorithm.Graph;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;
import com.nestorria.server.modules.user.UserRepository;

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
    private final UserRepository userRepository;

    public UserSimilarityGraphService(
            BookingRepository bookingRepository,
            PropertyRepository propertyRepository,
            UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
    }

    /**
     * Construye el grafo bipartito User ↔ Property.
     * Cada usuario está conectado con las propiedades que reservó (no canceladas).
     * Time: O(U × P) — scan de todas las reservas
     * Space: O(U + P + E) — grafo bipartito
     */
    @Transactional(readOnly = true)
    public Graph<String> buildBipartiteGraph() {
        Graph<String> graph = new Graph<>();

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
            if (allPropertyIds.contains(propertyId)) {
                graph.addVertex(propertyId);
                graph.addEdge(userId, propertyId);
            }
        }

        log.info("Grafo bipartito construido: {} vértices, {} aristas",
            graph.size(), countEdges(graph));

        return graph;
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
        Graph<String> graph = buildBipartiteGraph();

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

        // Nivel 2: usuarios que reservaron esas propiedades
        for (String propertyId : userProperties) {
            for (String neighbor : graph.getNeighbors(propertyId)) {
                if (!visited.contains(neighbor) && neighbor.startsWith("user_")) {
                    // heuristic: user IDs start with "user_" or are UUIDs
                    // En este proyecto los user IDs son strings de Clerk
                    similarUsers.add(neighbor);
                    visited.add(neighbor);
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

        // 1. Obtener propiedades del usuario
        List<Booking> userBookings = bookingRepository.findByUserId(userId);
        Set<String> ownPropertyIds = userBookings.stream()
            .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
            .map(b -> b.getProperty().getId())
            .collect(Collectors.toSet());

        // 2. Encontrar usuarios similares
        List<String> similarUserIds = findSimilarUsers(userId, 20);

        if (similarUserIds.isEmpty()) {
            return List.of();
        }

        // 3. Obtener propiedades de usuarios similares, excluyendo las propias
        Map<String, Long> propertyScore = new HashMap<>();

        for (String similarUserId : similarUserIds) {
            List<Booking> similarBookings = bookingRepository.findByUserId(similarUserId);
            for (Booking b : similarBookings) {
                if (b.getStatus() != BookingStatus.CANCELLED
                        && !ownPropertyIds.contains(b.getProperty().getId())) {
                    propertyScore.merge(b.getProperty().getId(), 1L, Long::sum);
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
     * a través de propiedades compartidas.
     * Time: O(V + E)
     */
    @Transactional(readOnly = true)
    public List<Set<String>> findUserCommunities() {
        Graph<String> graph = buildBipartiteGraph();
        List<Set<String>> allComponents = graph.connectedComponents();

        // Filtrar solo componentes que contengan al menos un usuario
        return allComponents.stream()
            .filter(component -> component.size() > 1)
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
