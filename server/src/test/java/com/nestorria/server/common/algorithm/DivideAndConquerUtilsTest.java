package com.nestorria.server.common.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DivideAndConquerUtilsTest {

    private static ExecutorService executor;
    private final Comparator<Integer> intComparator = Comparator.naturalOrder();

    @BeforeAll
    static void setUp() {
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterAll
    static void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    // MERGE SORT TESTS
    @Test
    void mergeSort_emptyList() {
        assertEquals(List.of(), DivideAndConquerUtils.mergeSort(List.of(), intComparator));
    }

    @Test
    void mergeSort_singleElement() {
        assertEquals(List.of(1), DivideAndConquerUtils.mergeSort(List.of(1), intComparator));
    }

    @Test
    void mergeSort_alreadySorted() {
        assertEquals(List.of(1, 2, 3, 4, 5), 
            DivideAndConquerUtils.mergeSort(List.of(1, 2, 3, 4, 5), intComparator));
    }

    @Test
    void mergeSort_reverseSorted() {
        assertEquals(List.of(1, 2, 3, 4, 5), 
            DivideAndConquerUtils.mergeSort(List.of(5, 4, 3, 2, 1), intComparator));
    }

    @Test
    void mergeSort_randomOrder() {
        assertEquals(List.of(1, 2, 3, 4, 5), 
            DivideAndConquerUtils.mergeSort(List.of(3, 1, 5, 2, 4), intComparator));
    }

    @Test
    void mergeSort_duplicates() {
        assertEquals(List.of(1, 1, 2, 2, 3), 
            DivideAndConquerUtils.mergeSort(List.of(2, 1, 3, 1, 2), intComparator));
    }

    @Test
    void mergeSort_allEqual() {
        assertEquals(List.of(5, 5, 5, 5), 
            DivideAndConquerUtils.mergeSort(List.of(5, 5, 5, 5), intComparator));
    }

    @Test
    void mergeSort_negativeValues() {
        assertEquals(List.of(-5, -3, -1, 0, 2), 
            DivideAndConquerUtils.mergeSort(List.of(0, -3, 2, -5, -1), intComparator));
    }

    @Test
    void mergeSort_isStable() {
        record Item(String value, int originalIndex) {}
        Comparator<Item> comp = Comparator.comparing(Item::value);
        
        List<Item> items = List.of(
            new Item("b", 0), new Item("a", 1), new Item("b", 2), new Item("a", 3)
        );
        List<Item> sorted = DivideAndConquerUtils.mergeSort(items, comp);
        
        assertEquals("a", sorted.get(0).value());
        assertEquals(1, sorted.get(0).originalIndex());
        assertEquals("a", sorted.get(1).value());
        assertEquals(3, sorted.get(1).originalIndex());
        assertEquals("b", sorted.get(2).value());
        assertEquals(0, sorted.get(2).originalIndex());
        assertEquals("b", sorted.get(3).value());
        assertEquals(2, sorted.get(3).originalIndex());
    }

    @Test
    void mergeSort_doesNotMutateInput() {
        List<Integer> original = List.of(3, 1, 2);
        List<Integer> sorted = DivideAndConquerUtils.mergeSort(original, intComparator);
        
        assertEquals(List.of(3, 1, 2), original);
        assertEquals(List.of(1, 2, 3), sorted);
    }

    @Test
    void mergeSort_nullList() {
        assertEquals(List.of(), DivideAndConquerUtils.mergeSort(null, intComparator));
    }

    // BINARY SEARCH TESTS
    @Test
    void binarySearch_findsExistingElement() {
        List<Integer> sorted = List.of(10, 20, 30, 40, 50);
        assertEquals(2, DivideAndConquerUtils.<Integer, Integer>binarySearch(sorted, 30, n -> n));
    }

    @Test
    void binarySearch_returnsInsertionPoint() {
        List<Integer> sorted = List.of(10, 20, 40, 50);
        assertEquals(2, DivideAndConquerUtils.<Integer, Integer>binarySearch(sorted, 30, n -> n));
    }

    @Test
    void binarySearch_firstElement() {
        List<Integer> sorted = List.of(10, 20, 30);
        assertEquals(0, DivideAndConquerUtils.<Integer, Integer>binarySearch(sorted, 10, n -> n));
    }

    @Test
    void binarySearch_lastElement() {
        List<Integer> sorted = List.of(10, 20, 30);
        assertEquals(2, DivideAndConquerUtils.<Integer, Integer>binarySearch(sorted, 30, n -> n));
    }

    @Test
    void binarySearch_emptyList() {
        assertEquals(0, DivideAndConquerUtils.<Integer, Integer>binarySearch(List.of(), 10, n -> n));
    }

    @Test
    void binarySearch_nullList() {
        assertEquals(0, DivideAndConquerUtils.<Integer, Integer>binarySearch(null, 10, n -> n));
    }

    @Test
    void binarySearch_nullTarget() {
        List<Integer> sorted = List.of(10, 20);
        assertEquals(0, DivideAndConquerUtils.<Integer, Integer>binarySearch(sorted, null, n -> n));
    }

    @Test
    void binarySearch_withCustomExtractor() {
        record Property(String id, int price) {}
        List<Property> sorted = List.of(
            new Property("a", 100), new Property("b", 200), new Property("c", 300)
        );
        assertEquals(1, DivideAndConquerUtils.binarySearch(sorted, 200, Property::price));
    }

    // RANGE SEARCH TESTS
    @Test
    void rangeSearch_returnsElementsInRange() {
        List<Integer> sorted = List.of(10, 20, 30, 40, 50);
        List<Integer> result = DivideAndConquerUtils.<Integer, Integer>rangeSearch(sorted, 20, 40, n -> n);
        assertEquals(List.of(20, 30, 40), result);
    }

    @Test
    void rangeSearch_returnsEmptyWhenLowGreaterThanHigh() {
        List<Integer> sorted = List.of(10, 20, 30);
        List<Integer> result = DivideAndConquerUtils.<Integer, Integer>rangeSearch(sorted, 40, 20, n -> n);
        assertTrue(result.isEmpty());
    }

    @Test
    void rangeSearch_returnsEmptyOnEmptyList() {
        List<Integer> result = DivideAndConquerUtils.<Integer, Integer>rangeSearch(List.of(), 1, 10, n -> n);
        assertTrue(result.isEmpty());
    }

    @Test
    void rangeSearch_returnsEmptyOnNull() {
        List<Integer> result = DivideAndConquerUtils.<Integer, Integer>rangeSearch(null, 1, 10, n -> n);
        assertTrue(result.isEmpty());
    }

    @Test
    void rangeSearch_noMatchOutsideRange() {
        List<Integer> sorted = List.of(10, 20, 30);
        List<Integer> result = DivideAndConquerUtils.<Integer, Integer>rangeSearch(sorted, 100, 200, n -> n);
        assertTrue(result.isEmpty());
    }

    // QUICKSELECT TESTS
    @Test
    void quickselect_findsKthElement() {
        List<Integer> list = new ArrayList<>(List.of(3, 1, 4, 1, 5, 9, 2, 6));
        assertEquals(1, DivideAndConquerUtils.quickselect(list, 0));  // min
        assertEquals(9, DivideAndConquerUtils.quickselect(list, 7));  // max
        assertEquals(4, DivideAndConquerUtils.quickselect(list, 3));  // middle
    }

    @Test
    void quickselect_singleElement() {
        List<Integer> list = new ArrayList<>(List.of(42));
        assertEquals(42, DivideAndConquerUtils.quickselect(list, 0));
    }

    @Test
    void quickselect_allEqual_doesNotRecurseOneElementAtATime() {
        // Regression: three-way partition must group all equals in one pass,
        // so even 1000 identical elements resolve in O(n) without O(n²) recursion.
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 1000; i++) list.add(7);
        assertEquals(7, DivideAndConquerUtils.quickselect(list, 0));
        assertEquals(7, DivideAndConquerUtils.quickselect(list, 499));
        assertEquals(7, DivideAndConquerUtils.quickselect(list, 999));
    }

    @Test
    void quickselect_throwsOnInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> 
            DivideAndConquerUtils.quickselect(new ArrayList<Integer>(), 0));
        assertThrows(IllegalArgumentException.class, () -> 
            DivideAndConquerUtils.quickselect(new ArrayList<>(List.of(1, 2)), -1));
        assertThrows(IllegalArgumentException.class, () -> 
            DivideAndConquerUtils.quickselect(new ArrayList<>(List.of(1, 2)), 2));
    }

    // MEDIAN TESTS
    @Test
    void median_oddNumberOfElements() {
        List<Integer> numbers = List.of(1, 2, 3, 4, 5);
        OptionalDouble result = DivideAndConquerUtils.median(numbers);
        assertTrue(result.isPresent());
        assertEquals(3.0, result.getAsDouble());
    }

    @Test
    void median_evenNumberOfElements() {
        List<Integer> numbers = List.of(1, 2, 3, 4);
        OptionalDouble result = DivideAndConquerUtils.median(numbers);
        assertTrue(result.isPresent());
        assertEquals(2.5, result.getAsDouble());
    }

    @Test
    void median_emptyList() {
        OptionalDouble result = DivideAndConquerUtils.median(List.of());
        assertFalse(result.isPresent());
    }

    @Test
    void median_nullList() {
        OptionalDouble result = DivideAndConquerUtils.median(null);
        assertFalse(result.isPresent());
    }

    @Test
    void median_allNulls() {
        java.util.List<Integer> numbers = Arrays.asList(null, null, null);
        OptionalDouble result = DivideAndConquerUtils.median(numbers);
        assertFalse(result.isPresent());
    }

    @Test
    void median_withNulls() {
        java.util.List<Integer> numbers = Arrays.asList(1, null, 3, null, 5);
        OptionalDouble result = DivideAndConquerUtils.median(numbers);
        assertTrue(result.isPresent());
        assertEquals(3.0, result.getAsDouble());
    }

    // FILTER PARALLEL TESTS
    @Test
    void filterParallel_emptyList() {
        List<Integer> result = DivideAndConquerUtils.filterParallel(
            List.of(), n -> n > 5, executor, 10
        );
        assertTrue(result.isEmpty());
    }

    @Test
    void filterParallel_invalidChunkSize_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            DivideAndConquerUtils.filterParallel(List.of(1, 2, 3), n -> n > 1, executor, 0));
        assertThrows(IllegalArgumentException.class, () ->
            DivideAndConquerUtils.filterParallel(List.of(1, 2, 3), n -> n > 1, executor, -1));
    }

    @Test
    void filterParallel_filtersCorrectly() {
        List<Integer> input = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<Integer> result = DivideAndConquerUtils.filterParallel(
            input, n -> n > 5, executor, 3
        );
        assertEquals(List.of(6, 7, 8, 9, 10), result);
    }

    // COUNT BY PARALLEL TESTS
    @Test
    void countByParallel_emptyList() {
        Map<String, Long> result = DivideAndConquerUtils.<String, String>countByParallel(
            List.of(), s -> s, executor, 10
        );
        assertTrue(result.isEmpty());
    }

    @Test
    void countByParallel_countsCorrectly() {
        List<String> input = List.of("apple", "banana", "avocado", "blueberry", "cherry");
        Map<String, Long> result = DivideAndConquerUtils.<String, String>countByParallel(
            input, s -> s.substring(0, 1), executor, 2
        );
        assertEquals(2L, result.get("a"));
        assertEquals(2L, result.get("b"));
        assertEquals(1L, result.get("c"));
    }

    // MAP PARALLEL TESTS
    @Test
    void mapParallel_emptyList() {
        List<String> result = DivideAndConquerUtils.mapParallel(
            List.of(), Object::toString, executor, 10
        );
        assertTrue(result.isEmpty());
    }

    @Test
    void mapParallel_transformsCorrectly() {
        List<Integer> input = List.of(1, 2, 3, 4, 5);
        List<String> result = DivideAndConquerUtils.mapParallel(
            input, n -> "item-" + n, executor, 2
        );
        assertEquals(List.of("item-1", "item-2", "item-3", "item-4", "item-5"), result);
    }
}
