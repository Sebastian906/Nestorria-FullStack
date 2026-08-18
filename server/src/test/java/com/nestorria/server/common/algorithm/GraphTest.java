package com.nestorria.server.common.algorithm;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class GraphTest {

    // Basic operations
    @Test
    void addVertex_addsVertex() {
        Graph<String> graph = new Graph<>();
        graph.addVertex("A");
        assertTrue(graph.containsVertex("A"));
        assertEquals(1, graph.size());
    }

    @Test
    void addVertex_duplicateDoesNothing() {
        Graph<String> graph = new Graph<>();
        graph.addVertex("A");
        graph.addVertex("A");
        assertEquals(1, graph.size());
    }

    @Test
    void addEdge_createsBothVerticesAndEdges() {
        Graph<String> graph = new Graph<>();
        graph.addEdge("A", "B");
        assertEquals(2, graph.size());
        assertTrue(graph.getNeighbors("A").contains("B"));
        assertTrue(graph.getNeighbors("B").contains("A"));
    }

    @Test
    void removeEdge_removesOnlyEdgeNotVertices() {
        Graph<String> graph = new Graph<>();
        graph.addEdge("A", "B");
        graph.removeEdge("A", "B");
        assertFalse(graph.getNeighbors("A").contains("B"));
        assertFalse(graph.getNeighbors("B").contains("A"));
        assertTrue(graph.containsVertex("A"));
        assertTrue(graph.containsVertex("B"));
    }

    // BFS
    @Test
    void bfs_findsShortestPathUnweighted() {
        Graph<String> graph = new Graph<>();
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "D");
        graph.addEdge("C", "D");
        graph.addEdge("D", "E");

        Optional<List<String>> path = graph.bfs("A", "E");
        assertTrue(path.isPresent());
        assertEquals(List.of("A", "B", "D", "E"), path.get());
    }

    @Test
    void bfs_sameSourceAndTarget() {
        Graph<String> graph = new Graph<>();
        graph.addVertex("A");

        Optional<List<String>> path = graph.bfs("A", "A");
        assertTrue(path.isPresent());
        assertEquals(List.of("A"), path.get());
    }

    @Test
    void bfs_noPathExists() {
        Graph<String> graph = new Graph<>();
        graph.addVertex("A");
        graph.addVertex("B");

        Optional<List<String>> path = graph.bfs("A", "B");
        assertTrue(path.isEmpty());
    }

    @Test
    void bfs_nonExistentVertex() {
        Graph<String> graph = new Graph<>();
        graph.addVertex("A");

        Optional<List<String>> path = graph.bfs("A", "Z");
        assertTrue(path.isEmpty());
    }

    @Test
    void bfs_linearGraph() {
        Graph<Integer> graph = new Graph<>();
        graph.addEdge(1, 2);
        graph.addEdge(2, 3);
        graph.addEdge(3, 4);
        graph.addEdge(4, 5);

        Optional<List<Integer>> path = graph.bfs(1, 5);
        assertTrue(path.isPresent());
        assertEquals(List.of(1, 2, 3, 4, 5), path.get());
    }

    // DFS / Connected Components
    @Test
    void connectedComponents_singleComponent() {
        Graph<String> graph = new Graph<>();
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");

        List<Set<String>> components = graph.connectedComponents();
        assertEquals(1, components.size());
        assertEquals(3, components.get(0).size());
    }

    @Test
    void connectedComponents_multipleComponents() {
        Graph<String> graph = new Graph<>();
        graph.addEdge("A", "B");
        graph.addEdge("C", "D");
        graph.addVertex("E");

        List<Set<String>> components = graph.connectedComponents();
        assertEquals(3, components.size());
    }

    @Test
    void connectedComponents_emptyGraph() {
        Graph<String> graph = new Graph<>();
        List<Set<String>> components = graph.connectedComponents();
        assertTrue(components.isEmpty());
    }

    @Test
    void connectedComponents_singleVertex() {
        Graph<String> graph = new Graph<>();
        graph.addVertex("A");

        List<Set<String>> components = graph.connectedComponents();
        assertEquals(1, components.size());
        assertEquals(Set.of("A"), components.get(0));
    }

    // Dijkstra
    @Test
    void dijkstra_findsShortestWeightedPath() {
        Graph<String> graph = new Graph<>();
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "D");
        graph.addEdge("C", "D");
        graph.addEdge("D", "E");

        // A-B=1, A-C=5, B-D=2, C-D=1, D-E=3
        java.util.Map<String, java.util.Map<String, Double>> weights = new java.util.HashMap<>();
        weights.computeIfAbsent("A", k -> new java.util.HashMap<>()).put("B", 1.0);
        weights.computeIfAbsent("B", k -> new java.util.HashMap<>()).put("A", 1.0);
        weights.computeIfAbsent("A", k -> new java.util.HashMap<>()).put("C", 5.0);
        weights.computeIfAbsent("C", k -> new java.util.HashMap<>()).put("A", 5.0);
        weights.computeIfAbsent("B", k -> new java.util.HashMap<>()).put("D", 2.0);
        weights.computeIfAbsent("D", k -> new java.util.HashMap<>()).put("B", 2.0);
        weights.computeIfAbsent("C", k -> new java.util.HashMap<>()).put("D", 1.0);
        weights.computeIfAbsent("D", k -> new java.util.HashMap<>()).put("C", 1.0);
        weights.computeIfAbsent("D", k -> new java.util.HashMap<>()).put("E", 3.0);
        weights.computeIfAbsent("E", k -> new java.util.HashMap<>()).put("D", 3.0);

        Optional<List<String>> path = graph.dijkstra("A", "E",
            (a, b) -> weights.getOrDefault(a, java.util.Map.of())
                .getOrDefault(b, Double.POSITIVE_INFINITY));

        assertTrue(path.isPresent());
        assertEquals(List.of("A", "B", "D", "E"), path.get());
    }

    @Test
    void dijkstra_noPathExists() {
        Graph<String> graph = new Graph<>();
        graph.addVertex("A");
        graph.addVertex("B");

        Optional<List<String>> path = graph.dijkstra("A", "B",
            (a, b) -> Double.POSITIVE_INFINITY);
        assertTrue(path.isEmpty());
    }

    @Test
    void dijkstra_sameSourceAndTarget() {
        Graph<String> graph = new Graph<>();
        graph.addVertex("A");

        Optional<List<String>> path = graph.dijkstra("A", "A",
            (a, b) -> 1.0);
        assertTrue(path.isPresent());
        assertEquals(List.of("A"), path.get());
    }

    // Prim MST
    @Test
    void primMST_findsMinimumSpanningTree() {
        Graph<String> graph = new Graph<>();
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "D");
        graph.addEdge("C", "D");
        graph.addEdge("D", "E");

        java.util.Map<String, java.util.Map<String, Double>> weights = new java.util.HashMap<>();
        weights.computeIfAbsent("A", k -> new java.util.HashMap<>()).put("B", 1.0);
        weights.computeIfAbsent("B", k -> new java.util.HashMap<>()).put("A", 1.0);
        weights.computeIfAbsent("A", k -> new java.util.HashMap<>()).put("C", 5.0);
        weights.computeIfAbsent("C", k -> new java.util.HashMap<>()).put("A", 5.0);
        weights.computeIfAbsent("B", k -> new java.util.HashMap<>()).put("D", 2.0);
        weights.computeIfAbsent("D", k -> new java.util.HashMap<>()).put("B", 2.0);
        weights.computeIfAbsent("C", k -> new java.util.HashMap<>()).put("D", 1.0);
        weights.computeIfAbsent("D", k -> new java.util.HashMap<>()).put("C", 1.0);
        weights.computeIfAbsent("D", k -> new java.util.HashMap<>()).put("E", 3.0);
        weights.computeIfAbsent("E", k -> new java.util.HashMap<>()).put("D", 3.0);

        Graph<String> mst = graph.primMST("A",
            (a, b) -> weights.getOrDefault(a, java.util.Map.of())
                .getOrDefault(b, Double.POSITIVE_INFINITY));

        // MST should have V-1 edges = 4 edges
        int edgeCount = 0;
        for (String v : mst.getVertices()) {
            edgeCount += mst.getNeighbors(v).size();
        }
        assertEquals(8, edgeCount / 2); // 4 edges × 2 directions
    }

    @Test
    void primMST_nonExistentStartVertex() {
        Graph<String> graph = new Graph<>();
        graph.addVertex("A");

        Graph<String> mst = graph.primMST("Z", (a, b) -> 1.0);
        assertTrue(mst.size() == 0);
    }

    // Integer graph
    @Test
    void bfs_integerGraph() {
        Graph<Integer> graph = new Graph<>();
        graph.addEdge(1, 2);
        graph.addEdge(2, 3);
        graph.addEdge(3, 4);

        Optional<List<Integer>> path = graph.bfs(1, 4);
        assertTrue(path.isPresent());
        assertEquals(List.of(1, 2, 3, 4), path.get());
    }
}
