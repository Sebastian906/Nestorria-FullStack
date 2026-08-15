package com.nestorria.server.common.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Utilidades genéricas de divide-and-conquer para procesamiento de colecciones.
 * Diseñado para operar sobre datos ya cargados (e.g. desde cache Caffeine)
 * sin realizar queries adicionales a la base de datos.
 * Complejidad temporal de cada operación:
 * - mergeSort:         O(n log n) estable, peor caso garantizado
 * - binarySearch:      O(log n) para listas ordenadas
 * - quickselect:       O(n) promedio, O(n²) peor caso (three-way partition)
 * - filterParallel:    O(n/p) donde p = número de threads
 * - countByParallel:   O(n/p) donde p = número de threads
 * - mapParallel:       O(n/p) donde p = número de threads
 * - median:            O(n) promedio via quickselect
 */
public final class DivideAndConquerUtils {

    private DivideAndConquerUtils() {}

    // MERGE SORT
    /**
     * Merge Sort genérico con Comparator.
     * Garantiza O(n log n) en el peor caso y es estable (elementos iguales mantienen orden original).
     * Time:  O(n log n) garantizado
     * Space: O(n)
     * Estable: Sí
     * Uso recomendado: Cuando se necesita ordenamiento estable sobre listas que no están cacheadas
     * o cuando se necesita un Comparator personalizado que no puede aplicarse con Stream.sorted().
     */
    public static <T> List<T> mergeSort(List<T> list, Comparator<T> comparator) {
        if (list == null || list.size() <= 1) {
            return list != null ? List.copyOf(list) : List.of();
        }
        return new ArrayList<>(mergeSortInternal(new ArrayList<>(list), comparator));
    }

    private static <T> ArrayList<T> mergeSortInternal(ArrayList<T> list, Comparator<T> comparator) {
        if (list.size() <= 1) return list;
        
        int mid = list.size() / 2;
        ArrayList<T> left = new ArrayList<>(list.subList(0, mid));
        ArrayList<T> right = new ArrayList<>(list.subList(mid, list.size()));
        
        left = mergeSortInternal(left, comparator);
        right = mergeSortInternal(right, comparator);
        
        return merge(left, right, comparator);
    }

    private static <T> ArrayList<T> merge(ArrayList<T> left, ArrayList<T> right, Comparator<T> comparator) {
        ArrayList<T> result = new ArrayList<>(left.size() + right.size());
        int i = 0, j = 0;
        
        while (i < left.size() && j < right.size()) {
            if (comparator.compare(left.get(i), right.get(j)) <= 0) {
                result.add(left.get(i++));
            } else {
                result.add(right.get(j++));
            }
        }
        
        result.addAll(left.subList(i, left.size()));
        result.addAll(right.subList(j, right.size()));
        return result;
    }

    // BINARY SEARCH
    /**
     * Búsqueda binaria genérica sobre lista ordenada.
     * Retorna el índice donde el target debería insertarse para mantener el orden.
     * Time:  O(log n)
     * Space: O(1)
     * Precondición: la lista debe estar ordenada según keyExtractor
     * Uso recomendado: Cuando se necesita encontrar la posición de inserción
     * o buscar un elemento en una lista ya ordenada (ej: propiedades ordenadas por precio).
     */
    public static <T, K extends Comparable<K>> int binarySearch(
            List<T> sortedList, K target, Function<T, K> keyExtractor) {
        if (sortedList == null || sortedList.isEmpty() || target == null) {
            return 0;
        }
        
        int low = 0;
        int high = sortedList.size() - 1;
        
        while (low <= high) {
            int mid = low + (high - low) / 2;
            int cmp = keyExtractor.apply(sortedList.get(mid)).compareTo(target);
            if (cmp < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * Búsqueda por rango sobre lista ordenada usando binary search.
     * Retorna todos los elementos donde low <= keyExtractor(element) <= high.
     * Time:  O(log n + k) donde k = tamaño del resultado
     * Space: O(k)
     * Precondición: la lista debe estar ordenada según keyExtractor
     * Uso recomendado: Filtrado de propiedades por rango de precio cuando ya están ordenadas.
     * Más eficiente que filterByRange O(n) para listas ordenadas.
     */
    public static <T, K extends Comparable<K>> List<T> rangeSearch(
            List<T> sortedList, K low, K high, Function<T, K> keyExtractor) {
        if (sortedList == null || sortedList.isEmpty() || low == null || high == null) {
            return List.of();
        }
        if (low.compareTo(high) > 0) {
            return List.of();
        }
        
        int start = binarySearch(sortedList, low, keyExtractor);
        int end = start;
        while (end < sortedList.size()
                && keyExtractor.apply(sortedList.get(end)).compareTo(high) <= 0) {
            end++;
        }
        return sortedList.subList(start, end);
    }

    // QUICKSELECT (for median)
    /**
     * Quickselect: encuentra el k-ésimo elemento sin ordenar completamente la lista.
     * Divide-and-conquer variant de Quick Sort.
     * Usa three-way partitioning para manejar duplicados eficientemente.
     * Time:  O(n) promedio, O(n²) peor caso (mitigado con pivot aleatorio)
     * Space: O(1) in-place
     * Uso recomendado: Encontrar mediana o percentiles sin ordenar toda la lista.
     * NOTA: Modifica la lista internamente. Para listas inmutables, crear copia primero.
     */
    public static <T extends Comparable<T>> T quickselect(List<T> list, int k) {
        if (list == null || list.isEmpty() || k < 0 || k >= list.size()) {
            throw new IllegalArgumentException("Invalid input: list=" + list + ", k=" + k);
        }

        ArrayList<T> workingList = new ArrayList<>(list);
        int low = 0, high = workingList.size() - 1;

        while (low <= high) {
            if (low == high) return workingList.get(low);

            int[] bounds = threeWayPartition(workingList, low, high);
            int lt = bounds[0], gt = bounds[1];

            if (k < lt) {
                high = lt - 1;
            } else if (k > gt) {
                low = gt + 1;
            } else {
                return workingList.get(k);
            }
        }

        throw new IllegalStateException("quickselect failed — should never reach here");
    }

    /**
     * Three-way partitioning (Dutch National Flag): agrupa elementos en [lt, gt]
     * en三部分: menores, iguales, mayores al pivot.
     * Devuelve [lt, gt] donde [lt..gt] son todos iguales al pivot.
     * Time: O(n) — un solo pase sobre la porción.
     */
    private static <T extends Comparable<T>> int[] threeWayPartition(
            ArrayList<T> list, int low, int high) {
        // Pivot aleatorio para evitar peor caso
        int randomIndex = low + (int) (Math.random() * (high - low + 1));
        swap(list, randomIndex, low);

        T pivot = list.get(low);
        int lt = low;   // lt = boundary of elements < pivot
        int i = low + 1; // current scanner
        int gt = high;   // gt = boundary of elements > pivot

        while (i <= gt) {
            int cmp = list.get(i).compareTo(pivot);
            if (cmp < 0) {
                swap(list, lt, i);
                lt++;
                i++;
            } else if (cmp > 0) {
                swap(list, i, gt);
                gt--;
            } else {
                i++;
            }
        }

        return new int[]{lt, gt};
    }

    private static <T> void swap(ArrayList<T> list, int i, int j) {
        T temp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, temp);
    }

    // MEDIAN
    /**
     * Calcula la mediana de una lista de números.
     * Usa quickselect O(n) promedio para encontrar el/los elementos del medio.
     * Time:  O(n) promedio, O(n²) peor caso (mitigado con pivot aleatorio)
     * Space: O(n) para la copia interna de quickselect
     * Uso recomendado: Estadísticas de precio (min, max, avg, median).
     * Retorna Optional.empty() si la lista está vacía o todos los valores son null.
     */
    public static OptionalDouble median(List<? extends Number> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            return OptionalDouble.empty();
        }

        List<Double> valid = numbers.stream()
            .filter(n -> n != null)
            .map(Number::doubleValue)
            .toList();

        if (valid.isEmpty()) {
            return OptionalDouble.empty();
        }

        int size = valid.size();
        if (size % 2 == 1) {
            return OptionalDouble.of(quickselect(valid, size / 2));
        } else {
            double mid1 = quickselect(valid, size / 2 - 1);
            double mid2 = quickselect(valid, size / 2);
            return OptionalDouble.of((mid1 + mid2) / 2.0);
        }
    }

    // PARALLEL PROCESSING

    /**
     * Divide una lista en chunks del tamaño indicado.
     * Rechaza chunkSize <= 0 con IllegalArgumentException.
     */
    private static <T> List<List<T>> splitIntoChunks(List<T> items, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be > 0, got: " + chunkSize);
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += chunkSize) {
            chunks.add(items.subList(i, Math.min(i + chunkSize, items.size())));
        }
        return chunks;
    }

    /**
     * Filtra una lista en paralelo usando divide-and-conquer.
     * Divide la lista en chunks, filtra cada chunk en un thread separado,
     * y combina los resultados.
     * Time:  O(n/p) donde p = número de threads
     * Space: O(n)
     * Uso recomendado: Filtrado de propiedades por múltiples criterios cuando
     * el filtrado es costoso (ej: llamadas a servicios externos).
     */
    public static <T> List<T> filterParallel(
            List<T> items,
            Predicate<T> predicate,
            Executor executor,
            int chunkSize) {

        if (items == null || items.isEmpty()) {
            return List.of();
        }

        if (items.size() <= chunkSize) {
            return items.stream().filter(predicate).toList();
        }

        List<List<T>> chunks = splitIntoChunks(items, chunkSize);

        List<CompletableFuture<List<T>>> futures = chunks.stream()
            .map(chunk -> CompletableFuture.supplyAsync(
                () -> chunk.stream().filter(predicate).toList(), executor))
            .toList();

        List<T> result = new ArrayList<>();
        for (CompletableFuture<List<T>> future : futures) {
            result.addAll(future.join());
        }
        return result;
    }

    /**
     * Cuenta elementos en paralelo usando divide-and-conquer.
     * Divide la lista en chunks, cuenta cada chunk en un thread separado,
     * y combina los mapas resultantes.
     * Time:  O(n/p) donde p = número de threads
     * Space: O(n)
     * Uso recomendado: Conteo de propiedades por ciudad, tipo, etc.
     */
    public static <T, K> java.util.Map<K, Long> countByParallel(
            List<T> items,
            Function<T, K> keyExtractor,
            Executor executor,
            int chunkSize) {

        if (items == null || items.isEmpty()) {
            return java.util.Map.of();
        }

        if (items.size() <= chunkSize) {
            return SearchUtils.countBy(items, keyExtractor);
        }

        List<List<T>> chunks = splitIntoChunks(items, chunkSize);

        List<CompletableFuture<java.util.Map<K, Long>>> futures = chunks.stream()
            .map(chunk -> CompletableFuture.supplyAsync(
                () -> SearchUtils.countBy(chunk, keyExtractor), executor))
            .toList();

        java.util.Map<K, Long> result = new java.util.HashMap<>();
        for (CompletableFuture<java.util.Map<K, Long>> future : futures) {
            future.join().forEach((key, count) -> result.merge(key, count, Long::sum));
        }
        return result;
    }

    /**
     * Aplica una función a cada elemento en paralelo usando divide-and-conquer.
     * Divide la lista en chunks, aplica la función en cada chunk en un thread separado,
     * y combina los resultados.
     * Time:  O(n/p) donde p = número de threads
     * Space: O(n)
     * Uso recomendado: Transformación de entidades a DTOs cuando la transformación
     * es costosa (ej: construir objetos complejos con múltiples campos).
     */
    public static <T, R> List<R> mapParallel(
            List<T> items,
            Function<T, R> mapper,
            Executor executor,
            int chunkSize) {

        if (items == null || items.isEmpty()) {
            return List.of();
        }

        if (items.size() <= chunkSize) {
            return items.stream().map(mapper).toList();
        }

        List<List<T>> chunks = splitIntoChunks(items, chunkSize);

        List<CompletableFuture<List<R>>> futures = chunks.stream()
            .map(chunk -> CompletableFuture.supplyAsync(
                () -> chunk.stream().map(mapper).toList(), executor))
            .toList();

        List<R> result = new ArrayList<>();
        for (CompletableFuture<List<R>> future : futures) {
            result.addAll(future.join());
        }
        return result;
    }
}
