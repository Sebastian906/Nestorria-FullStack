package com.nestorria.server.common.algorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Utilidades genéricas de búsqueda y agregación sobre colecciones en memoria.
 * Diseñado para operar sobre datos ya cargados (e.g. desde cache Caffeine)
 * sin realizar queries adicionales a la base de datos.
 * Complejidad temporal de cada operación:
 * - filter:      O(n)
 * - groupBy:     O(n)
 * - countBy:     O(n)
 * - searchByAny: O(n × m) donde m = número de extractores de texto
 */
public final class SearchUtils {

    private SearchUtils() {}

    /**
     * Filtra una lista retornando todos los elementos que cumplen el predicado.
     * Complejidad: O(n).
     */
    public static <T> List<T> filter(List<T> list, Predicate<T> predicate) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().filter(predicate).toList();
    }

    /**
     * Agrupa elementos por una clave extraída.
     * Retorna un Map donde cada clave tiene la lista de elementos que la poseen.
     * Complejidad: O(n).
     */
    public static <T, K> Map<K, List<T>> groupBy(List<T> list, Function<T, K> keyExtractor) {
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        Map<K, List<T>> result = new HashMap<>();
        for (T item : list) {
            result.computeIfAbsent(keyExtractor.apply(item), k -> new ArrayList<>()).add(item);
        }
        return result;
    }

    /**
     * Cuenta elementos por una clave extraída.
     * Retorna un Map donde cada clave tiene el número de elementos que la poseen.
     * Complejidad: O(n).
     */
    public static <T, K> Map<K, Long> countBy(List<T> list, Function<T, K> keyExtractor) {
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        Map<K, Long> result = new HashMap<>();
        for (T item : list) {
            result.merge(keyExtractor.apply(item), 1L, Long::sum);
        }
        return result;
    }

    /**
     * Busca elementos cuyo texto extraído contenga el query (case-insensitive).
     * Acepta múltiples extractores de texto para buscar en varios campos.
     * Complejidad: O(n × m) donde m = número de extractores.
     * Ejemplo: searchByAny(properties, query, Property::getTitle, Property::getCity)
     */
    @SafeVarargs
    public static <T> List<T> searchByAny(
            List<T> list, String query, Function<T, String>... extractors) {
        if (list == null || list.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        String lowerQuery = query.toLowerCase();
        return list.stream()
            .filter(item -> {
                for (Function<T, String> extractor : extractors) {
                    String value = extractor.apply(item);
                    if (value != null && value.toLowerCase().contains(lowerQuery)) {
                        return true;
                    }
                }
                return false;
            })
            .toList();
    }

    /**
     * Filtra una lista por un rango numérico dado un extractor de valor.
     * Retorna todos los elementos donde low <= extractor(element) <= high.
     * Complejidad: O(n).
     * NOTA: Para listas ordenadas, binary search sería O(log n), pero el coste
     * de ordenar O(n log n) supera el beneficio para colecciones pequeñas/medianas.
     * Esta implementación directa es más eficiente para el caso real.
     */
    public static <T, V extends Comparable<V>> List<T> filterByRange(
            List<T> list, V low, V high, Function<T, V> valueExtractor) {
        if (list == null || list.isEmpty() || low == null || high == null) {
            return List.of();
        }
        if (low.compareTo(high) > 0) {
            return List.of();
        }
        return list.stream()
            .filter(item -> {
                V value = valueExtractor.apply(item);
                return value != null && value.compareTo(low) >= 0 && value.compareTo(high) <= 0;
            })
            .toList();
    }
}
