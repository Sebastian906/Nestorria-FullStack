package com.nestorria.server.common.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Utilidades genéricas de algoritmos greedy.
 * Diseñado para operar sobre datos ya cargados sin queries adicionales a la base de datos.
 * Complejidad temporal de cada operación:
 * - sortByPriority:      O(n log n) — sorting compuesto
 * - intervalScheduling:  O(n log n) — sorting + una pasada O(n)
 */
public final class GreedyUtils {

    private GreedyUtils() {}

    /**
     * Ordena elementos por función de prioridad (mayor = primero) usando
     * comparadores compuestos. Equivalent to: sort by priority descending.
     * Time:  O(n log n)
     * Space: O(n)
     * @param items         — elementos a ordenar
     * @param comparators   — comparadores en orden de prioridad (primero = mayor prioridad)
     * @return lista ordenada por prioridad descendente
     */
    @SafeVarargs
    public static <T> List<T> sortByPriority(
            List<T> items, Comparator<T>... comparators) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (comparators.length == 0) {
            return List.copyOf(items);
        }

        Comparator<T> composite = comparators[0];
        for (int i = 1; i < comparators.length; i++) {
            composite = composite.thenComparing(comparators[i]);
        }

        return items.stream()
            .sorted(composite)
            .toList();
    }

    /**
     * Interval Scheduling: selecciona el máximo número de intervalos no solapados.
     * Algoritmo greedy clásico: ordena por tiempo de finalización, toma los que terminan primero.
     * Propiedad greedy: siempre seleccionar el intervalo que termina antes deja
     * el máximo espacio disponible para intervalos futuros. Esto produce la solución óptima.
     * Time:  O(n log n) — dominado por el sorting
     * Space: O(n) — para la copia ordenada
     * @param intervals  — lista de intervalos
     * @param startGetter — función para obtener la fecha de inicio
     * @param endGetter   — función para obtener la fecha de fin
     * @return lista de intervalos no solapados seleccionados greedy
     */
    public static <T, C extends Comparable<C>> List<T> intervalScheduling(
            List<T> intervals,
            Function<T, C> startGetter,
            Function<T, C> endGetter) {

        if (intervals == null || intervals.isEmpty()) {
            return List.of();
        }

        // Copiar para no mutar la lista original
        List<T> sorted = new ArrayList<>(intervals);

        // Ordenar por end time ascendente (greedy: earliest finishing first)
        Comparator<T> byEnd = (a, b) -> endGetter.apply(a).compareTo(endGetter.apply(b));

        sorted.sort(byEnd);

        List<T> selected = new ArrayList<>();
        C lastEnd = null;

        for (T interval : sorted) {
            C start = startGetter.apply(interval);
            if (lastEnd == null || start.compareTo(lastEnd) >= 0) {
                selected.add(interval);
                lastEnd = endGetter.apply(interval);
            }
        }

        return List.copyOf(selected);
    }
}
