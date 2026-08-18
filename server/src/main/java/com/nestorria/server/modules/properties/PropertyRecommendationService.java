package com.nestorria.server.modules.properties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.algorithm.Graph;
import com.nestorria.server.modules.booking.Booking;
import com.nestorria.server.modules.booking.BookingRepository;
import com.nestorria.server.modules.booking.BookingStatus;
import com.nestorria.server.modules.favorite.Favorite;
import com.nestorria.server.modules.favorite.FavoriteRepository;
import com.nestorria.server.modules.properties.dto.PropertyResponse;
import com.nestorria.server.modules.properties.dto.PropertySummaryResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de recomendación de propiedades basado en graph algorithms.
 *
 * Construye un grafo de similitud donde:
 * - Cada vértice es una propiedad (por ID)
 * - Cada arista tiene un peso calculado por similitud de atributos
 * - BFS desde una propiedad encuentra las más similares por proximidad
 *
 * Complejidad:
 * - buildSimilarityGraph: O(N² × A) donde N = propiedades, A = costo de comparación
 * - getSimilarProperties: O(V + E) para BFS sobre el grafo cacheado
 * - getRecommendations: O(N) sobre reservas del usuario
 *
 * RENDIMIENTO:
 * - El grafo se cachea con Caffeine (cacheNames = "propertySimilarityGraph")
 * - Con más de ~5000 propiedades, considerar pre-computación batch
 */
@Service
@Slf4j
public class PropertyRecommendationService {

    private static final double CITY_MATCH_WEIGHT = 3.0;
    private static final double TYPE_MATCH_WEIGHT = 2.0;
    private static final double PRICE_MATCH_WEIGHT = 1.0;
    private static final double AMENITY_MATCH_WEIGHT = 1.0;
    private static final double PRICE_RANGE_THRESHOLD = 0.3; // 30% tolerance

    private final PropertyRepository propertyRepository;
    private final BookingRepository bookingRepository;
    private final FavoriteRepository favoriteRepository;
    private final PropertyRecommendationService self;

    public PropertyRecommendationService(
            PropertyRepository propertyRepository,
            BookingRepository bookingRepository,
            FavoriteRepository favoriteRepository,
            @Lazy PropertyRecommendationService self) {
        this.propertyRepository = propertyRepository;
        this.bookingRepository = bookingRepository;
        this.favoriteRepository = favoriteRepository;
        // En Spring llega el proxy @Lazy; en tests directos se usa this
        this.self = self != null ? self : this;
    }

    /**
     * Construye el grafo de similitud entre propiedades.
     * Caché para evitar reconstrucción en cada request.
     *
     * Time: O(N²) donde N = propiedades disponibles
     * Space: O(N + E) donde E = aristas con similitud > 0
     *
     * ⚠️ LIMITACIÓN: Con más de ~5000 propiedades, este método será lento.
     * Para escalar: pre-computar en batch programado (nightly job).
     */
    @Cacheable(cacheNames = "propertySimilarityGraph", key = "'similarity'")
    @Transactional(readOnly = true)
    public Graph<String> buildSimilarityGraph() {
        List<Property> properties = propertyRepository.findByIsAvailableTrue();
        Graph<String> graph = new Graph<>();

        for (Property p : properties) {
            graph.addVertex(p.getId());
        }

        // O(N²) — aceptable para < 5000 propiedades
        for (int i = 0; i < properties.size(); i++) {
            for (int j = i + 1; j < properties.size(); j++) {
                Property a = properties.get(i);
                Property b = properties.get(j);
                double weight = calculateSimilarity(a, b);
                if (weight > 0) {
                    graph.addEdge(a.getId(), b.getId());
                }
            }
        }

        log.info("Grafo de similitud construido: {} vértices, {} aristas",
            graph.size(), countEdges(graph));

        return graph;
    }

    /**
     * Calcula la similitud entre dos propiedades.
     * Retorna un peso acumulado de los criterios coincidentes.
     *
     * Criterios:
     * - Misma ciudad: +3
     * - Mismo tipo de propiedad: +2
     * - Precio similar (dentro de 30%): +1
     * - Amenities compartidos: +1 por cada amenity compartido
     *
     * Time: O(min(A₁, A₂)) donde A = número de amenities
     */
    double calculateSimilarity(Property a, Property b) {
        double weight = 0;

        // Misma ciudad: +3
        if (a.getCity() != null && a.getCity().equals(b.getCity())) {
            weight += CITY_MATCH_WEIGHT;
        }

        // Mismo tipo: +2
        if (a.getPropertyType() == b.getPropertyType()) {
            weight += TYPE_MATCH_WEIGHT;
        }

        // Precio similar: +1
        if (isPriceSimilar(a, b)) {
            weight += PRICE_MATCH_WEIGHT;
        }

        // Amenities compartidos: +1 cada uno
        weight += countSharedAmenities(a.getAmenities(), b.getAmenities()) * AMENITY_MATCH_WEIGHT;

        return weight;
    }

    /**
     * Encuentra propiedades similares usando BFS desde la propiedad dada.
     * Retorna las propiedades más cercanas en el grafo de similitud.
     *
     * Time: O(V + E) para BFS, O(K log K) para ordenar por peso
     * Space: O(V)
     */
    @Transactional(readOnly = true)
    public List<PropertySummaryResponse> getSimilarProperties(String propertyId, int limit) {
        // A través del proxy para que @Cacheable de buildSimilarityGraph se aplique
        Graph<String> graph = self.buildSimilarityGraph();

        if (!graph.containsVertex(propertyId)) {
            return List.of();
        }

        // BFS limitado: nunca agrega más de `limit` resultados
        Set<String> visited = new HashSet<>();
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        queue.add(propertyId);
        visited.add(propertyId);

        List<String> similar = new ArrayList<>();
        while (!queue.isEmpty() && similar.size() < limit) {
            String current = queue.poll();
            for (String neighbor : graph.getNeighbors(current)) {
                if (similar.size() >= limit) break;
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    similar.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        if (similar.isEmpty()) {
            return List.of();
        }

        List<Property> properties = propertyRepository.findAllById(similar);

        // Mantener el orden de BFS (más cercano primero)
        Map<String, Property> propertyMap = new HashMap<>();
        for (Property p : properties) {
            propertyMap.put(p.getId(), p);
        }

        return similar.stream()
            .filter(propertyMap::containsKey)
            .map(id -> PropertySummaryResponse.fromEntity(propertyMap.get(id)))
            .toList();
    }

    /**
     * Recomendaciones personalizadas para un usuario:
     * 1. Propiedades de las mismas agencias que ya reservó
     * 2. Propiedades en las mismas ciudades que tiene en favoritos
     * 3. Propiedades similares a las que reservó
     *
     * Time: O(N) donde N = reservas + favoritos del usuario
     */
    @Transactional(readOnly = true)
    public List<PropertySummaryResponse> getRecommendations(String userId, int limit) {
        // 1. Obtener propiedades reservadas por el usuario
        List<Booking> userBookings = bookingRepository.findByUserId(userId);
        Set<String> bookedPropertyIds = userBookings.stream()
            .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
            .map(b -> b.getProperty().getId())
            .collect(java.util.stream.Collectors.toSet());

        // 2. Obtener propiedades en favoritos
        List<Favorite> favorites = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Set<String> favoritePropertyIds = favorites.stream()
            .map(f -> f.getProperty().getId())
            .collect(java.util.stream.Collectors.toSet());

        // 3. Obtener agencias y ciudades de interés
        Set<String> interestedAgencyIds = new HashSet<>();
        Set<String> interestedCities = new HashSet<>();

        List<Property> bookedProperties = propertyRepository.findAllById(new ArrayList<>(bookedPropertyIds));
        for (Property p : bookedProperties) {
            interestedAgencyIds.add(p.getAgency().getId());
            if (p.getCity() != null) interestedCities.add(p.getCity());
        }

        List<Property> favoritedProperties = propertyRepository.findAllById(new ArrayList<>(favoritePropertyIds));
        for (Property p : favoritedProperties) {
            interestedAgencyIds.add(p.getAgency().getId());
            if (p.getCity() != null) interestedCities.add(p.getCity());
        }

        // 4. Buscar propiedades de las agencias/ciudades de interés, excluyendo las ya reservadas/favoritas
        Set<String> excludeIds = new HashSet<>();
        excludeIds.addAll(bookedPropertyIds);
        excludeIds.addAll(favoritePropertyIds);

        List<Property> allAvailable = propertyRepository.findByIsAvailableTrue();
        List<PropertySummaryResponse> candidates = allAvailable.stream()
            .filter(p -> !excludeIds.contains(p.getId()))
            .filter(p -> interestedAgencyIds.contains(p.getAgency().getId())
                || interestedCities.contains(p.getCity()))
            .map(PropertySummaryResponse::fromEntity)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        // 5. Si no hay suficientes candidatos, rellenar con propiedades similares a las reservadas
        if (candidates.size() < limit && !bookedPropertyIds.isEmpty()) {
            Set<String> additionalIds = new HashSet<>();
            for (String bookedId : bookedPropertyIds) {
                List<PropertySummaryResponse> similar = getSimilarProperties(bookedId, limit);
                for (PropertySummaryResponse s : similar) {
                    if (!excludeIds.contains(s.id()) && !additionalIds.contains(s.id())) {
                        additionalIds.add(s.id());
                        candidates.add(s);
                        if (candidates.size() >= limit) break;
                    }
                }
                if (candidates.size() >= limit) break;
            }
        }

        return candidates.stream().limit(limit).toList();
    }

    // --- Private helpers ---

    private boolean isPriceSimilar(Property a, Property b) {
        if (a.getPrice() == null || b.getPrice() == null) return false;

        Integer priceA = a.getPrice().getSale() != null ? a.getPrice().getSale() : a.getPrice().getRent();
        Integer priceB = b.getPrice().getSale() != null ? b.getPrice().getSale() : b.getPrice().getRent();

        if (priceA == null || priceB == null) return false;
        if (priceA == 0 || priceB == 0) return false;

        double diff = Math.abs((double) priceA - priceB);
        double avg = (priceA + priceB) / 2.0;
        return diff / avg <= PRICE_RANGE_THRESHOLD;
    }

    private long countSharedAmenities(List<String> amenitiesA, List<String> amenitiesB) {
        if (amenitiesA == null || amenitiesB == null) return 0;
        Set<String> setA = new HashSet<>(amenitiesA);
        Set<String> setB = new HashSet<>(amenitiesB);
        setA.retainAll(setB);
        return setA.size();
    }

    private int countEdges(Graph<String> graph) {
        int count = 0;
        for (String vertex : graph.getVertices()) {
            count += graph.getNeighbors(vertex).size();
        }
        return count / 2; // grafo no dirigido, cada arista contada 2 veces
    }
}
