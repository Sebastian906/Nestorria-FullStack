package com.nestorria.server.common.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Estructura de datos genérica de grafo no dirigido con lista de adyacencia.
 * Utiliza Map<T, Set<T>> internamente para representar adyacencias.
 * Complejidad temporal:
 * - addVertex:      O(1)
 * - addEdge:        O(1)
 * - removeEdge:     O(1)
 * - getNeighbors:   O(1)
 * - bfs:            O(V + E)
 * - dfs:            O(V + E)
 * - connectedComponents: O(V + E)
 * - dijkstra:       O((V + E) log V)
 * - primMST:        O(E log V)
 * donde V = número de vértices, E = número de aristas.
 * NOTA: Para grafos con más de ~10,000 vértices, considerar
 * representaciones más eficientes o procesamiento en lotes.
 */
public class Graph<T> {

    private final Map<T, Set<T>> adjacencyList = new HashMap<>();

    /**
     * Agrega un vértice al grafo.
     * Si ya existe, no hace nada.
     * Time: O(1)
     */
    public void addVertex(T vertex) {
        adjacencyList.computeIfAbsent(vertex, k -> new LinkedHashSet<>());
    }

    /**
     * Agrega una arista no dirigida entre source y destination.
     * Crea ambos vértices si no existen.
     * Time: O(1)
     */
    public void addEdge(T source, T destination) {
        addVertex(source);
        addVertex(destination);
        adjacencyList.get(source).add(destination);
        adjacencyList.get(destination).add(source);
    }

    /**
     * Elimina la arista entre source y destination.
     * No elimina los vértices.
     * Time: O(1)
     */
    public void removeEdge(T source, T destination) {
        Set<T> neighbors = adjacencyList.get(source);
        if (neighbors != null) {
            neighbors.remove(destination);
        }
        Set<T> reverseNeighbors = adjacencyList.get(destination);
        if (reverseNeighbors != null) {
            reverseNeighbors.remove(source);
        }
    }

    /**
     * Retorna los vecinos de un vértice.
     * Time: O(1)
     */
    public Set<T> getNeighbors(T vertex) {
        return adjacencyList.getOrDefault(vertex, Set.of());
    }

    // Retorna todos los vértices del grafo.
    public Set<T> getVertices() {
        return adjacencyList.keySet();
    }

    // Retorna el número de vértices.
    public int size() {
        return adjacencyList.size();
    }

    // Verifica si el grafo contiene un vértice.
    public boolean containsVertex(T vertex) {
        return adjacencyList.containsKey(vertex);
    }

    /**
     * BFS: encuentra el camino más corto (no ponderado) entre source y target.
     * Retorna una lista con el camino, o vacío si no hay camino.
     * Time:  O(V + E)
     * Space: O(V)
     */
    public Optional<List<T>> bfs(T source, T target) {
        if (!containsVertex(source) || !containsVertex(target)) {
            return Optional.empty();
        }
        if (source.equals(target)) {
            return Optional.of(List.of(source));
        }

        Map<T, T> parent = new HashMap<>();
        Queue<T> queue = new LinkedList<>();
        Set<T> visited = new HashSet<>();

        queue.add(source);
        visited.add(source);
        parent.put(source, null);

        while (!queue.isEmpty()) {
            T current = queue.poll();
            for (T neighbor : getNeighbors(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, current);
                    queue.add(neighbor);

                    if (neighbor.equals(target)) {
                        return Optional.of(reconstructPath(parent, target));
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * DFS: encuentra todos los componentes conectados del grafo.
     * Cada componente es un Set de vértices mutuamente alcanzables.
     * Time:  O(V + E)
     * Space: O(V)
     */
    public List<Set<T>> connectedComponents() {
        List<Set<T>> components = new ArrayList<>();
        Set<T> globalVisited = new HashSet<>();

        for (T vertex : getVertices()) {
            if (!globalVisited.contains(vertex)) {
                Set<T> component = new LinkedHashSet<>();
                dfsVisit(vertex, globalVisited, component);
                components.add(component);
            }
        }

        return components;
    }

    /**
     * Dijkstra: encuentra el camino más corto con pesos no negativos.
     * weightFunction(a, b) retorna el peso de la arista entre a y b.
     * Si no hay arista, debe retornar Double.POSITIVE_INFINITY o similar.
     * Time:  O((V + E) log V) con PriorityQueue
     * Space: O(V)
     */
    public Optional<List<T>> dijkstra(
            T source, T target,
            BiFunction<T, T, Double> weightFunction) {

        if (!containsVertex(source) || !containsVertex(target)) {
            return Optional.empty();
        }
        if (source.equals(target)) {
            return Optional.of(List.of(source));
        }

        Map<T, Double> dist = new HashMap<>();
        Map<T, T> parent = new HashMap<>();
        PriorityQueue<NodeWithDist<T>> pq = new PriorityQueue<>(
            Comparator.comparingDouble((NodeWithDist<T> n) -> n.dist));

        for (T v : getVertices()) {
            dist.put(v, Double.POSITIVE_INFINITY);
        }
        dist.put(source, 0.0);
        pq.add(new NodeWithDist<>(source, 0.0));

        while (!pq.isEmpty()) {
            NodeWithDist<T> current = pq.poll();
            if (current.dist > dist.getOrDefault(current.vertex, Double.POSITIVE_INFINITY)) {
                continue;
            }
            if (current.vertex.equals(target)) {
                return Optional.of(reconstructPath(parent, target));
            }

            for (T neighbor : getNeighbors(current.vertex)) {
                double weight = weightFunction.apply(current.vertex, neighbor);
                double newDist = current.dist + weight;
                if (newDist < dist.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    dist.put(neighbor, newDist);
                    parent.put(neighbor, current.vertex);
                    pq.add(new NodeWithDist<>(neighbor, newDist));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Prim: encuentra el Minimum Spanning Tree del grafo.
     * El grafo debe ser conexo. Si no lo es, retorna el MST del componente
     * que contiene el vértice inicial.
     * Time:  O(E log V)
     * Space: O(V)
     * Retorna un nuevo Graph<T> con las aristas del MST.
     */
    public Graph<T> primMST(T startVertex, BiFunction<T, T, Double> weightFunction) {
        Graph<T> mst = new Graph<>();
        if (!containsVertex(startVertex)) {
            return mst;
        }

        Set<T> inMST = new HashSet<>();
        PriorityQueue<Edge<T>> pq = new PriorityQueue<>(
            Comparator.comparingDouble((Edge<T> e) -> e.weight));

        mst.addVertex(startVertex);
        inMST.add(startVertex);

        for (T neighbor : getNeighbors(startVertex)) {
            double weight = weightFunction.apply(startVertex, neighbor);
            pq.add(new Edge<>(startVertex, neighbor, weight));
        }

        while (!pq.isEmpty() && inMST.size() < adjacencyList.size()) {
            Edge<T> minEdge = pq.poll();
            if (inMST.contains(minEdge.to)) {
                continue;
            }

            mst.addVertex(minEdge.to);
            mst.addEdge(minEdge.from, minEdge.to);
            inMST.add(minEdge.to);

            for (T neighbor : getNeighbors(minEdge.to)) {
                if (!inMST.contains(neighbor)) {
                    double weight = weightFunction.apply(minEdge.to, neighbor);
                    pq.add(new Edge<>(minEdge.to, neighbor, weight));
                }
            }
        }

        return mst;
    }

    // Internal helpers
    private List<T> reconstructPath(Map<T, T> parent, T target) {
        List<T> path = new ArrayList<>();
        T current = target;
        while (current != null) {
            path.add(current);
            current = parent.get(current);
        }
        java.util.Collections.reverse(path);
        return path;
    }

    private void dfsVisit(T vertex, Set<T> visited, Set<T> component) {
        visited.add(vertex);
        component.add(vertex);
        for (T neighbor : getNeighbors(vertex)) {
            if (!visited.contains(neighbor)) {
                dfsVisit(neighbor, visited, component);
            }
        }
    }

    private record NodeWithDist<T>(T vertex, double dist) {}
    private record Edge<T>(T from, T to, double weight) {}
}
