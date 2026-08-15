package com.nestorria.server.common.algorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Utilidades genéricas de Dynamic Programming (Programación Dinámica).
 * Tipos de DP soportados:
 * - Bottom-Up (Tabulation): Construye la solución desde subproblemas base
 * - Top-Down (Memoization): Resuelve recursivamente con cache
 * - Prefix-Sum: Sumas prefijas para rangos
 * - Acumulación: Agregación en una sola pasada
 * Complejidad de cada operación:
 * - bottomUp: O(n) tiempo, O(n) espacio
 * - topDown: O(n) tiempo, O(n) espacio (con memoización)
 * - prefixSum: O(n) preprocesamiento, O(1) por query
 * - accumulate: O(n) tiempo, O(1) espacio extra
 */
public final class DynamicProgrammingUtils {

    private DynamicProgrammingUtils() {}

    // BOTTOM-UP (TABULATION)
    /**
     * Bottom-Up DP: construye la solución desde el caso base.
     * @param n - tamaño del problema (número de subproblemas)
     * @param baseCase - valor para el caso base (dp[0])
     * @param transition - función de transición: dp[i] = f(i, dp[0..i-1])
     * @return el valor dp[n]
     * Ejemplo de uso:
     * // Calcular Fibonacci
     * long fib = DynamicProgrammingUtils.bottomUp(10, 0L, (i, dp) -> dp[i-1] + dp[i-2]);
     */
    public static long bottomUp(int n, long baseCase, 
                                 BiFunction<Integer, long[], Long> transition) {
        if (n < 0) throw new IllegalArgumentException("n must be >= 0");
        if (n == 0) return baseCase;

        long[] dp = new long[n + 1];
        dp[0] = baseCase;

        for (int i = 1; i <= n; i++) {
            dp[i] = transition.apply(i, dp);
        }

        return dp[n];
    }

    /**
     * Bottom-Up DP con reconstrucción de camino.
     * @param n - tamaño del problema
     * @param baseCase - valor para el caso base
     * @param transition - función de transición
     * @param choice - función que registra qué opción se eligió en cada paso
     * @return Par de [valor final, camino de decisiones]
     */
    public static <T> Map.Entry<Long, List<T>> bottomUpWithPath(
            int n, long baseCase,
            BiFunction<Integer, long[], Long> transition,
            BiFunction<Integer, long[], T> choice) {

        if (n < 0) throw new IllegalArgumentException("n must be >= 0");

        long[] dp = new long[n + 1];
        List<T> choices = new ArrayList<>();

        dp[0] = baseCase;

        for (int i = 1; i <= n; i++) {
            dp[i] = transition.apply(i, dp);
            choices.add(choice.apply(i, dp));
        }
        
        return Map.entry(dp[n], choices);
    }

    // TOP-DOWN (MEMOIZATION)
    /**
     * Top-Down DP con memoización manual.
     * Resuelve el problema recursivamente cacheando resultados.
     * @param n - tamaño del problema
     * @param baseCase - función que retorna el valor para casos base
     * @param recursive - función recursiva: f(n) = g(n, f(0..n-1))
     * @return el valor f(n)
     * Ejemplo de uso:
     * // Calcular Fibonacci con memoización
     * long fib = DynamicProgrammingUtils.topDown(10, 
     *     i -> (long) i,  // baseCase: f(0)=0, f(1)=1
     *     (i, memo) -> memo[i-1] + memo[i-2]  // recursión
     * );
     */
    public static long topDown(int n, Function<Integer, Long> baseCase,
                                BiFunction<Integer, Long[], Long> recursive) {
        if (n < 0) throw new IllegalArgumentException("n must be >= 0");
        
        Long[] memo = new Long[n + 1];
        return topDownHelper(n, memo, baseCase, recursive);
    }
    
    private static long topDownHelper(int n, Long[] memo,
                                       Function<Integer, Long> baseCase,
                                       BiFunction<Integer, Long[], Long> recursive) {
        // Caso base
        if (n == 0) return baseCase.apply(0);
        
        // Ya está en el cache
        if (memo[n] != null) return memo[n];
        
        // Calcular y guardar en cache
        memo[n] = recursive.apply(n, memo);
        return memo[n];
    }

    /**
     * Top-Down DP con cache personalizado (no array).
     * Útil cuando el índice no es un entero secuencial.
     * @param key - clave del problema
     * @param cache - mapa de cache externo
     * @param baseCase - función que determina si es caso base
     * @param baseValue - función que retorna valor para caso base
     * @param recursive - función recursiva
     * @return el valor f(key)
     */
    public static <K> K topDownCustom(K key, Map<K, K> cache,
                                       Function<K, Boolean> baseCase,
                                       Function<K, K> baseValue,
                                       Function<K, K> recursive) {
        // Caso base
        if (baseCase.apply(key)) return baseValue.apply(key);
        
        // Ya está en el cache
        if (cache.containsKey(key)) return cache.get(key);
        
        // Calcular y guardar en cache
        K result = recursive.apply(key);
        cache.put(key, result);
        return result;
    }

    // PREFIX-SUM
    /**
     * Prefix-Sum: precalcula sumas acumuladas para queries de rango en O(1).
     * @param values - array de valores
     * @return array de sumas prefijas donde prefix[i] = sum(values[0..i-1])
     * Ejemplo de uso:
     * // Para calcular sum QUICK de values[l..r]:
     * long[] prefix = DynamicProgrammingUtils.prefixSum(values);
     * long rangeSum = prefix[r + 1] - prefix[l];
     */
    public static long[] prefixSum(long[] values) {
        if (values == null || values.length == 0) {
            return new long[0];
        }
        
        long[] prefix = new long[values.length + 1];
        prefix[0] = 0;
        
        for (int i = 0; i < values.length; i++) {
            prefix[i + 1] = prefix[i] + values[i];
        }
        
        return prefix;
    }

    /**
     * Prefix-Sum 2D: para matrices y rangos 2D.
     * @param matrix - matriz 2D de valores
     * @return matriz de sumas prefijas
     */
    public static long[][] prefixSum2D(long[][] matrix) {
        if (matrix == null || matrix.length == 0) {
            return new long[0][0];
        }
        
        int rows = matrix.length;
        int cols = matrix[0].length;
        long[][] prefix = new long[rows + 1][cols + 1];
        
        for (int i = 1; i <= rows; i++) {
            for (int j = 1; j <= cols; j++) {
                prefix[i][j] = matrix[i-1][j-1] 
                    + prefix[i-1][j] 
                    + prefix[i][j-1] 
                    - prefix[i-1][j-1];
            }
        }
        
        return prefix;
    }

    /**
     * Query de rango en O(1) usando prefix-sum 1D.
     * @param prefix - array de sumas prefijas
     * @param left - índice izquierdo (inclusivo)
     * @param right - índice derecho (inclusivo)
     * @return suma de values[left..right]
     */
    public static long rangeSum(long[] prefix, int left, int right) {
        if (prefix == null || left < 0 || right >= prefix.length - 1 || left > right) {
            throw new IllegalArgumentException("Invalid range or prefix array");
        }
        return prefix[right + 1] - prefix[left];
    }

    /**
     * Query de rango en O(1) usando prefix-sum 2D.
     * @param prefix - matriz de sumas prefijas
     * @param row1, col1 - esquina superior izquierda
     * @param row2, col2 - esquina inferior derecha
     * @return suma del subrectángulo
     */
    public static long rangeSum2D(long[][] prefix, int row1, int col1, 
                                   int row2, int col2) {
        if (prefix == null) throw new IllegalArgumentException("Prefix matrix is null");
        
        return prefix[row2 + 1][col2 + 1]
            - prefix[row1][col2 + 1]
            - prefix[row2 + 1][col1]
            + prefix[row1][col1];
    }

    // ACUMULACIÓN
    /**
     * Acumulación en una sola pasada: calcula múltiples estadísticas simultáneamente.
     * @param values - lista de valores
     * @return estadísticas acumuladas (min, max, sum, count, avg)
     */
    public static AccumulationResult accumulate(List<? extends Number> values) {
        if (values == null || values.isEmpty()) {
            return new AccumulationResult(0, 0, 0, 0, 0.0);
        }
        
        double min = Double.MAX_VALUE;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0;
        int count = 0;
        
        for (Number value : values) {
            if (value != null) {
                double v = value.doubleValue();
                min = Math.min(min, v);
                max = Math.max(max, v);
                sum += v;
                count++;
            }
        }
        
        double avg = count > 0 ? sum / count : 0.0;
        return new AccumulationResult(min, max, sum, count, avg);
    }

    /**
     * Acumulación con agrupación: calcula estadísticas por grupo en una pasada.
     * @param items - lista de elementos
     * @param keyExtractor - función para extraer la clave de agrupación
     * @param valueExtractor - función para extraer el valor numérico
     * @return mapa de clave → estadísticas
     */
    public static <T, K> Map<K, AccumulationResult> accumulateByGroup(
            List<T> items,
            Function<T, K> keyExtractor,
            Function<T, ? extends Number> valueExtractor) {
        
        Map<K, AccumulationResult> result = new HashMap<>();
        
        // Pre-acumular valores por grupo
        Map<K, List<Double>> valuesByGroup = new HashMap<>();
        
        for (T item : items) {
            K key = keyExtractor.apply(item);
            Number value = valueExtractor.apply(item);
            
            if (value != null) {
                valuesByGroup.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(value.doubleValue());
            }
        }
        
        // Calcular estadísticas por grupo
        for (Map.Entry<K, List<Double>> entry : valuesByGroup.entrySet()) {
            result.put(entry.getKey(), accumulate(entry.getValue()));
        }
        
        return result;
    }

    // Resultado de acumulación.
    public record AccumulationResult(
        double min,
        double max,
        double sum,
        int count,
        double average
    ) {}
}
