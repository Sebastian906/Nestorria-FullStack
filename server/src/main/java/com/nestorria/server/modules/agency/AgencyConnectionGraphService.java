package com.nestorria.server.modules.agency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nestorria.server.common.algorithm.Graph;
import com.nestorria.server.modules.booking.Booking;
import com.nestorria.server.modules.booking.BookingRepository;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de grafo de conectividad entre agencias a través de propiedades y reservas.
 * Grafo:
 * - Nodos: Agency IDs
 * - Aristas: dos agencias están conectadas si un usuario reservó propiedades de ambas
 * Uso:
 * - Encontrar agencias con clientes compartidos (BFS)
 * - Encontrar las agencias más "conectadas" (grado de vértice)
 * - Detectar comunidades de agencias (connected components via DFS)
 * Complejidad:
 * - buildAgencyGraph: O(U × A²) donde U = usuarios, A = agencias por usuario
 * - findConnectedAgencies: O(V + E)
 */
@Service
@Slf4j
public class AgencyConnectionGraphService {

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final AgencyRepository agencyRepository;

    public AgencyConnectionGraphService(
            BookingRepository bookingRepository,
            PropertyRepository propertyRepository,
            AgencyRepository agencyRepository) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.agencyRepository = agencyRepository;
    }

    /**
     * Construye el grafo de conectividad entre agencias.
     * Dos agencias están conectadas si al menos un usuario reservó propiedades de ambas.
     * Time: O(U × A²) — para cada usuario, comparar pares de agencias
     * Space: O(A + E)
     */
    @Transactional(readOnly = true)
    public Graph<String> buildAgencyGraph() {
        Graph<String> graph = new Graph<>();

        List<Agency> allAgencies = agencyRepository.findAll();
        for (Agency agency : allAgencies) {
            graph.addVertex(agency.getId());
        }

        // Agrupar reservas por usuario
        // Para cada usuario, obtener las agencias de sus reservas
        // Conectar agencias que comparten usuario
        Map<String, Set<String>> userAgencies = buildUserAgencyMap();

        for (Set<String> agencies : userAgencies.values()) {
            List<String> agencyList = new ArrayList<>(agencies);
            for (int i = 0; i < agencyList.size(); i++) {
                for (int j = i + 1; j < agencyList.size(); j++) {
                    graph.addEdge(agencyList.get(i), agencyList.get(j));
                }
            }
        }

        log.info("Grafo de agencias construido: {} vértices, {} aristas",
            graph.size(), countEdges(graph));

        return graph;
    }

    /**
     * Encuentra agencias conectadas a una agencia dada (BFS 1 salto).
     * Retorna agencias cuyos clientes también reservaron en la agencia dada.
     * Time: O(V + E)
     */
    @Transactional(readOnly = true)
    public List<String> findConnectedAgencies(String agencyId, int limit) {
        Graph<String> graph = buildAgencyGraph();

        if (!graph.containsVertex(agencyId)) {
            return List.of();
        }

        return graph.getNeighbors(agencyId).stream()
            .limit(Math.max(0, limit))
            .toList();
    }

    /**
     * Encuentra las agencias más conectadas (mayor degree).
     * Retorna las agencias ordenadas por número de conexiones.
     * Time: O(A log A) — sort por degree
     */
    @Transactional(readOnly = true)
    public List<AgencyConnection> getMostConnectedAgencies(int limit) {
        Graph<String> graph = buildAgencyGraph();

        return graph.getVertices().stream()
            .map(agencyId -> new AgencyConnection(
                agencyId,
                graph.getNeighbors(agencyId).size()))
            .sorted((a, b) -> Integer.compare(b.connectionCount(), a.connectionCount()))
            .limit(Math.max(0, limit))
            .toList();
    }

    /**
     * Detecta comunidades de agencias (componentes conectados del grafo).
     * Cada comunidad es un grupo de agencias que están interconectadas
     * a través de clientes compartidos. Se omiten agencias aisladas.
     * Time: O(V + E)
     */
    @Transactional(readOnly = true)
    public List<Set<String>> findAgencyCommunities() {
        Graph<String> graph = buildAgencyGraph();
        return graph.connectedComponents().stream()
            .filter(component -> component.size() > 1)
            .toList();
    }

    // Private helpers
    private Map<String, Set<String>> buildUserAgencyMap() {
        Map<String, Set<String>> userAgencies = new HashMap<>();

        Map<String, String> propertyToAgency = new HashMap<>();
        for (Property p : propertyRepository.findByIsAvailableTrue()) {
            propertyToAgency.put(p.getId(), p.getAgency().getId());
        }

        // Una sola pasada sobre reservas confirmadas (con user y property ya fetch-eados)
        for (Booking b : bookingRepository.findAllConfirmed()) {
            String agencyId = propertyToAgency.get(b.getProperty().getId());
            if (agencyId != null) {
                userAgencies
                    .computeIfAbsent(b.getUser().getId(), k -> new HashSet<>())
                    .add(agencyId);
            }
        }

        return userAgencies;
    }

    private int countEdges(Graph<String> graph) {
        int count = 0;
        for (String vertex : graph.getVertices()) {
            count += graph.getNeighbors(vertex).size();
        }
        return count / 2;
    }

    // Resultado de conexión de agencia.
    public record AgencyConnection(String agencyId, int connectionCount) {}
}
