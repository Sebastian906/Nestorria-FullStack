package com.nestorria.server.common.algorithm;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SearchUtilsTest {

    // filter 
    @Test
    void filter_returnsMatchingElements() {
        List<Integer> numbers = List.of(1, 2, 3, 4, 5);
        List<Integer> result = SearchUtils.filter(numbers, n -> n > 3);
        assertEquals(List.of(4, 5), result);
    }

    @Test
    void filter_returnsEmptyOnNoMatch() {
        List<Integer> numbers = List.of(1, 2, 3);
        List<Integer> result = SearchUtils.filter(numbers, n -> n > 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void filter_returnsEmptyOnEmptyList() {
        List<Integer> result = SearchUtils.filter(List.of(), n -> true);
        assertTrue(result.isEmpty());
    }

    @Test
    void filter_returnsEmptyOnNull() {
        List<Integer> result = SearchUtils.filter(null, n -> true);
        assertTrue(result.isEmpty());
    }

    // groupBy
    @Test
    void groupBy_groupsElementsCorrectly() {
        List<String> words = List.of("apple", "banana", "avocado", "blueberry", "cherry");
        Map<String, List<String>> result = SearchUtils.groupBy(words, w -> w.substring(0, 1));

        assertEquals(3, result.size());
        assertEquals(List.of("apple", "avocado"), result.get("a"));
        assertEquals(List.of("banana", "blueberry"), result.get("b"));
        assertEquals(List.of("cherry"), result.get("c"));
    }

    @Test
    void groupBy_returnsEmptyMapOnEmptyList() {
        Map<String, List<String>> result = SearchUtils.groupBy(List.of(), w -> w.substring(0, 1));
        assertTrue(result.isEmpty());
    }

    @Test
    void groupBy_returnsEmptyMapOnNull() {
        Map<String, List<String>> result = SearchUtils.groupBy(null, w -> w.substring(0, 1));
        assertTrue(result.isEmpty());
    }

    // countBy
    @Test
    void countBy_countsElementsCorrectly() {
        List<String> words = List.of("apple", "banana", "avocado", "blueberry", "cherry");
        Map<String, Long> result = SearchUtils.countBy(words, w -> w.substring(0, 1));

        assertEquals(2L, result.get("a"));
        assertEquals(2L, result.get("b"));
        assertEquals(1L, result.get("c"));
    }

    @Test
    void countBy_returnsEmptyMapOnEmptyList() {
        Map<String, Long> result = SearchUtils.<String, String>countBy(List.of(), w -> w);
        assertTrue(result.isEmpty());
    }

    @Test
    void countBy_returnsEmptyMapOnNull() {
        Map<String, Long> result = SearchUtils.<String, String>countBy(null, w -> w);
        assertTrue(result.isEmpty());
    }

    // searchByAny
    @Test
    void searchByAny_findsMatchInAnyField() {
        record Item(String name, String city) {}

        List<Item> items = List.of(
            new Item("Casa Madrid", "Madrid"),
            new Item("Piso Barcelona", "Barcelona"),
            new Item("Villa Valencia", "Valencia")
        );

        List<Item> result = SearchUtils.searchByAny(items, "mad", Item::name, Item::city);
        assertEquals(1, result.size());
        assertEquals("Casa Madrid", result.getFirst().name());
    }

    @Test
    void searchByAny_caseInsensitive() {
        record Item(String name, String city) {}

        List<Item> items = List.of(
            new Item("Casa Madrid", "Madrid"),
            new Item("Piso Barcelona", "Barcelona")
        );

        List<Item> result = SearchUtils.searchByAny(items, "MADRID", Item::name, Item::city);
        assertEquals(1, result.size());
    }

    @Test
    void searchByAny_returnsEmptyOnBlankQuery() {
        record Item(String name) {}
        List<Item> items = List.of(new Item("test"));

        assertTrue(SearchUtils.searchByAny(items, "", Item::name).isEmpty());
        assertTrue(SearchUtils.searchByAny(items, "  ", Item::name).isEmpty());
        assertTrue(SearchUtils.searchByAny(items, null, Item::name).isEmpty());
    }

    @Test
    void searchByAny_returnsEmptyOnEmptyList() {
        record Item(String name) {}
        assertTrue(SearchUtils.searchByAny(List.of(), "test", Item::name).isEmpty());
    }

    @Test
    void searchByAny_returnsEmptyOnNullList() {
        record Item(String name) {}
        assertTrue(SearchUtils.searchByAny(null, "test", Item::name).isEmpty());
    }

    @Test
    void searchByAny_handlesNullFieldValues() {
        record Item(String name, String city) {}

        List<Item> items = List.of(
            new Item(null, "Madrid"),
            new Item("Piso", null)
        );

        List<Item> result = SearchUtils.searchByAny(items, "mad", Item::name, Item::city);
        assertEquals(1, result.size());
        assertEquals("Madrid", result.getFirst().city());
    }

    // filterByRange
    @Test
    void filterByRange_returnsElementsInRange() {
        List<Integer> numbers = List.of(10, 20, 30, 40, 50);
        List<Integer> result = SearchUtils.<Integer, Integer>filterByRange(numbers, 20, 40, n -> n);
        assertEquals(List.of(20, 30, 40), result);
    }

    @Test
    void filterByRange_returnsEmptyWhenLowGreaterThanHigh() {
        List<Integer> numbers = List.of(10, 20, 30);
        List<Integer> result = SearchUtils.<Integer, Integer>filterByRange(numbers, 40, 20, n -> n);
        assertTrue(result.isEmpty());
    }

    @Test
    void filterByRange_returnsEmptyOnEmptyList() {
        List<Integer> result = SearchUtils.<Integer, Integer>filterByRange(List.of(), 1, 10, n -> n);
        assertTrue(result.isEmpty());
    }

    @Test
    void filterByRange_returnsEmptyOnNull() {
        List<Integer> result = SearchUtils.<Integer, Integer>filterByRange(null, 1, 10, n -> n);
        assertTrue(result.isEmpty());
    }

    @Test
    void filterByRange_handlesNullValuesInExtractor() {
        record Item(String name, Integer price) {}

        List<Item> items = List.of(
            new Item("A", 100),
            new Item("B", null),
            new Item("C", 300)
        );

        List<Item> result = SearchUtils.filterByRange(items, 50, 200, Item::price);
        assertEquals(1, result.size());
        assertEquals("A", result.getFirst().name());
    }

    // binarySearch
    @Test
    void binarySearch_findsCorrectIndex() {
        List<Integer> sorted = List.of(10, 20, 30, 40, 50);
        assertEquals(2, SearchUtils.<Integer, Integer>binarySearch(sorted, 30, n -> n));
    }

    @Test
    void binarySearch_returnsInsertionPoint() {
        List<Integer> sorted = List.of(10, 20, 40, 50);
        assertEquals(2, SearchUtils.<Integer, Integer>binarySearch(sorted, 30, n -> n));
    }

    @Test
    void binarySearch_returnsZeroOnEmptyList() {
        assertEquals(0, SearchUtils.<Integer, Integer>binarySearch(List.of(), 10, n -> n));
    }

    @Test
    void binarySearch_returnsZeroOnNull() {
        assertEquals(0, SearchUtils.<Integer, Integer>binarySearch(null, 10, n -> n));
    }

    @Test
    void binarySearch_returnsZeroOnNullTarget() {
        List<Integer> sorted = List.of(10, 20);
        assertEquals(0, SearchUtils.<Integer, Integer>binarySearch(sorted, null, n -> n));
    }

    // rangeSearch
    @Test
    void rangeSearch_returnsElementsInRange() {
        List<Integer> sorted = List.of(10, 20, 30, 40, 50);
        List<Integer> result = SearchUtils.<Integer, Integer>rangeSearch(sorted, 20, 40, n -> n);
        assertEquals(List.of(20, 30, 40), result);
    }

    @Test
    void rangeSearch_returnsEmptyWhenLowGreaterThanHigh() {
        List<Integer> sorted = List.of(10, 20, 30);
        List<Integer> result = SearchUtils.<Integer, Integer>rangeSearch(sorted, 40, 20, n -> n);
        assertTrue(result.isEmpty());
    }

    @Test
    void rangeSearch_returnsEmptyOnEmptyList() {
        List<Integer> result = SearchUtils.<Integer, Integer>rangeSearch(List.of(), 1, 10, n -> n);
        assertTrue(result.isEmpty());
    }

    @Test
    void rangeSearch_returnsEmptyOnNull() {
        List<Integer> result = SearchUtils.<Integer, Integer>rangeSearch(null, 1, 10, n -> n);
        assertTrue(result.isEmpty());
    }

    @Test
    void rangeSearch_returnsEmptyOnNullBounds() {
        List<Integer> sorted = List.of(10, 20);
        assertTrue(SearchUtils.<Integer, Integer>rangeSearch(sorted, null, 10, n -> n).isEmpty());
        assertTrue(SearchUtils.<Integer, Integer>rangeSearch(sorted, 10, null, n -> n).isEmpty());
    }

    @Test
    void rangeSearch_noMatchOutsideRange() {
        List<Integer> sorted = List.of(10, 20, 30);
        List<Integer> result = SearchUtils.<Integer, Integer>rangeSearch(sorted, 100, 200, n -> n);
        assertTrue(result.isEmpty());
    }
}
