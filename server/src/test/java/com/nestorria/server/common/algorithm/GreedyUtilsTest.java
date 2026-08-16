package com.nestorria.server.common.algorithm;

import java.time.LocalDate;
import java.util.List;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GreedyUtilsTest {

    // sortByPriority tests
    @Test
    void sortByPriority_sortsDescendingByPriority() {
        record Item(String name, int priority) {}
        Comparator<Item> byPriority = Comparator.comparingInt(Item::priority).reversed();

        List<Item> items = List.of(
            new Item("low", 1),
            new Item("high", 3),
            new Item("medium", 2)
        );

        List<Item> result = GreedyUtils.sortByPriority(items, byPriority);
        assertEquals("high", result.get(0).name());
        assertEquals("medium", result.get(1).name());
        assertEquals("low", result.get(2).name());
    }

    @Test
    void sortByPriority_compositeComparators() {
        record Invoice(String status, LocalDate dueDate, long amount) {}
        Comparator<Invoice> byOverdue = Comparator.comparing(
            (Invoice i) -> "OVERDUE".equals(i.status()) ? 0 : 1);
        Comparator<Invoice> byDue = Comparator.comparing(Invoice::dueDate);
        Comparator<Invoice> byAmount = Comparator.comparingLong(Invoice::amount);

        List<Invoice> invoices = List.of(
            new Invoice("PENDING", LocalDate.of(2026, 8, 20), 5000),
            new Invoice("OVERDUE", LocalDate.of(2026, 8, 10), 3000),
            new Invoice("PENDING", LocalDate.of(2026, 8, 15), 1000),
            new Invoice("OVERDUE", LocalDate.of(2026, 8, 5), 8000)
        );

        List<Invoice> result = GreedyUtils.sortByPriority(
            invoices, byOverdue, byDue, byAmount);

        // OVERDUE + earliest due first
        assertEquals(LocalDate.of(2026, 8, 5), result.get(0).dueDate());
        assertEquals(LocalDate.of(2026, 8, 10), result.get(1).dueDate());
        // PENDING + earliest due
        assertEquals(LocalDate.of(2026, 8, 15), result.get(2).dueDate());
        assertEquals(LocalDate.of(2026, 8, 20), result.get(3).dueDate());
    }

    @Test
    void sortByPriority_emptyList() {
        assertTrue(GreedyUtils.sortByPriority(List.of(), Comparator.comparingInt(Object::hashCode)).isEmpty());
    }

    @Test
    void sortByPriority_singleElement() {
        List<Integer> result = GreedyUtils.sortByPriority(
            List.of(42), Comparator.comparingInt(Integer::intValue).reversed());
        assertEquals(List.of(42), result);
    }

    @Test
    void sortByPriority_doesNotMutateInput() {
        record Item(String name, int priority) {}
        List<Item> original = List.of(new Item("a", 1), new Item("b", 2));
        GreedyUtils.sortByPriority(original, Comparator.comparingInt(Item::priority).reversed());
        assertEquals("a", original.get(0).name());
    }

    // intervalScheduling tests
    @Test
    void intervalScheduling_selectsMaximumNonOverlapping() {
        record Interval(String name, LocalDate start, LocalDate end) {}
        List<Interval> intervals = List.of(
            new Interval("A", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3)),
            new Interval("B", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 5)),
            new Interval("C", LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 6)),
            new Interval("D", LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 8)),
            new Interval("E", LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 10))
        );

        List<Interval> result = GreedyUtils.intervalScheduling(
            intervals, Interval::start, Interval::end);

        // Sorted by end: A(3), B(5), C(6), D(8), E(10)
        // Greedy: A (ends Jan 3) → C (starts Jan 3 ≥ Jan 3, ends Jan 6) → E (starts Jan 6 ≥ Jan 6)
        assertEquals(3, result.size());
        assertEquals("A", result.get(0).name());
        assertEquals("C", result.get(1).name());
        assertEquals("E", result.get(2).name());
    }

    @Test
    void intervalScheduling_emptyList() {
        // Lambdas nunca se invocan (lista vacía), solo necesitan compilar
        assertTrue(GreedyUtils.intervalScheduling(
            List.of(),
            d -> { throw new UnsupportedOperationException(); },
            d -> { throw new UnsupportedOperationException(); }).isEmpty());
    }

    @Test
    void intervalScheduling_singleElement() {
        record Interval(LocalDate start, LocalDate end) {}
        List<Interval> intervals = List.of(
            new Interval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5))
        );
        assertEquals(1, GreedyUtils.intervalScheduling(
            intervals, Interval::start, Interval::end).size());
    }

    @Test
    void intervalScheduling_allOverlap() {
        record Interval(LocalDate start, LocalDate end) {}
        List<Interval> intervals = List.of(
            new Interval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10)),
            new Interval(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 8)),
            new Interval(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 7))
        );
        List<Interval> result = GreedyUtils.intervalScheduling(
            intervals, Interval::start, Interval::end);
        assertEquals(1, result.size());
    }

    @Test
    void intervalScheduling_noOverlaps() {
        record Interval(LocalDate start, LocalDate end) {}
        List<Interval> intervals = List.of(
            new Interval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3)),
            new Interval(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 6)),
            new Interval(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 9))
        );
        List<Interval> result = GreedyUtils.intervalScheduling(
            intervals, Interval::start, Interval::end);
        assertEquals(3, result.size());
    }

    @Test
    void intervalScheduling_containedIntervals() {
        record Interval(LocalDate start, LocalDate end) {}
        // B and C are contained within A
        List<Interval> intervals = List.of(
            new Interval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10)),
            new Interval(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3)),
            new Interval(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 5))
        );
        List<Interval> result = GreedyUtils.intervalScheduling(
            intervals, Interval::start, Interval::end);
        // Greedy picks B (ends day 3) then C (starts day 4 = after B ends day 3)
        assertEquals(2, result.size());
    }

    @Test
    void intervalScheduling_doesNotMutateInput() {
        record Interval(LocalDate start, LocalDate end) {}
        List<Interval> original = List.of(
            new Interval(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 10)),
            new Interval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3))
        );
        GreedyUtils.intervalScheduling(original, Interval::start, Interval::end);
        assertEquals(LocalDate.of(2026, 1, 5), original.get(0).start());
    }
}
