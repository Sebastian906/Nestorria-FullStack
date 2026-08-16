package com.nestorria.server.common.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Utilidades genéricas de backtracking para problemas de satisfacción de restricciones.
 * Diseñado para operar sobre datos ya cargados sin queries adicionales a la base de datos.
 * Casos de uso típicos:
 * - Selección de subconjunto con restricciones (ej: combinar propiedades que cumplan capacidad + presupuesto)
 * - Permutaciones con restricciones (ej: asignación de recursos)
 * - N-Queens, Sudoku, coloración de grafos (referencia académica)
 * Complejidad temporal:
 * - findFirstSolution: O(b^d) peor caso, donde b = branching factor, d = depth
 *                     Significativamente menor con pruning efectivo
 * - findAllSolutions:  O(b^d) peor caso — puede ser exponencial
 * NOTA: Para problemas sin restricciones cruzadas (como verificar disponibilidad
 * de N propiedades independientes), usar un simple loop es O(n) y más eficiente.
 * Backtracking solo se justifica cuando las decisiones son interdependientes.
 */
public final class BacktrackingUtils {

    private BacktrackingUtils() {}

    /**
     * Estado del solver durante la exploración.
     * @param <T> tipo de elemento being assigned
     */
    public record State<T>(
        List<T> assigned,
        int depth
    ) {}

    /**
     * Resultado de una solución encontrada.
     * @param <T> tipo de elemento
     */
    public record Solution<T>(
        List<T> assignments,
        boolean isComplete
    ) {}

    /**
     * Encuentra la primera solución válida usando backtracking.
     * @param candidates    — función que genera candidatos para la posición actual
     * @param isValid       — predicate que verifica si el estado parcial es válido (pruning)
     * @param isComplete    — predicate que determina si la solución está completa
     * @param maxDepth      — profundidad máxima del árbol de búsqueda
     * @param <T>           — tipo de elemento
     * @return la primera solución encontrada, o vacío si no existe
     * Complejidad:
     * - Peor caso: O(b^d) donde b = branching factor promedio, d = maxDepth
     * - Con pruning efectivo: significativamente menor en la práctica
     * - Espacio: O(d) para el stack de recursión
     */
    public static <T> Optional<Solution<T>> findFirstSolution(
            Function<State<T>, List<T>> candidates,
            BiPredicate<List<T>, Integer> isValid,
            BiPredicate<List<T>, Integer> isComplete,
            int maxDepth) {

        return findFirstSolutionBacktrack(
            candidates, isValid, isComplete, new ArrayList<>(), 0, maxDepth);
    }

    private static <T> Optional<Solution<T>> findFirstSolutionBacktrack(
            Function<State<T>, List<T>> candidates,
            BiPredicate<List<T>, Integer> isValid,
            BiPredicate<List<T>, Integer> isComplete,
            List<T> current,
            int depth,
            int maxDepth) {

        // Pruning: depth limit
        if (depth > maxDepth) {
            return Optional.empty();
        }

        // Goal test: must be valid AND complete
        if (isValid.test(current, depth) && isComplete.test(current, depth)) {
            return Optional.of(new Solution<>(List.copyOf(current), true));
        }

        // Generar candidatos para esta posición
        State<T> state = new State<>(List.copyOf(current), depth);
        List<T> options = candidates.apply(state);

        for (T choice : options) {
            current.add(choice);

            // Pruning: verificar parcialidad
            if (isValid.test(current, depth + 1)) {
                Optional<Solution<T>> result = findFirstSolutionBacktrack(
                    candidates, isValid, isComplete, current, depth + 1, maxDepth);
                if (result.isPresent()) {
                    return result;
                }
            }

            current.removeLast(); // backtrack
        }

        return Optional.empty();
    }

    /**
     * Encuentra todas las soluciones válidas usando backtracking.
     * ADVERTENCIA: El número de soluciones puede ser exponencial.
     * Usar maxSolutions para limitar la exploración.
     * @param maxSolutions  — número máximo de soluciones a encontrar (0 = sin límite)
     * @return lista de todas las soluciones encontradas
     */
    public static <T> List<Solution<T>> findAllSolutions(
            Function<State<T>, List<T>> candidates,
            BiPredicate<List<T>, Integer> isValid,
            BiPredicate<List<T>, Integer> isComplete,
            int maxDepth,
            int maxSolutions) {

        List<Solution<T>> solutions = new ArrayList<>();
        findAllSolutionsBacktrack(
            candidates, isValid, isComplete, new ArrayList<>(), 0, maxDepth,
            solutions, maxSolutions);
        return solutions;
    }

    private static <T> void findAllSolutionsBacktrack(
            Function<State<T>, List<T>> candidates,
            BiPredicate<List<T>, Integer> isValid,
            BiPredicate<List<T>, Integer> isComplete,
            List<T> current,
            int depth,
            int maxDepth,
            List<Solution<T>> solutions,
            int maxSolutions) {

        if (depth > maxDepth) return;
        if (maxSolutions > 0 && solutions.size() >= maxSolutions) return;

        // Goal test: must be valid AND complete
        if (isValid.test(current, depth) && isComplete.test(current, depth)) {
            solutions.add(new Solution<>(List.copyOf(current), true));
            return;
        }

        State<T> state = new State<>(List.copyOf(current), depth);
        List<T> options = candidates.apply(state);

        for (T choice : options) {
            if (maxSolutions > 0 && solutions.size() >= maxSolutions) break;

            current.add(choice);

            if (isValid.test(current, depth + 1)) {
                findAllSolutionsBacktrack(
                    candidates, isValid, isComplete, current, depth + 1, maxDepth,
                    solutions, maxSolutions);
            }

            current.removeLast();
        }
    }
}
