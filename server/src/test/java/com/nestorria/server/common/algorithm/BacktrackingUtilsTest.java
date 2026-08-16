package com.nestorria.server.common.algorithm;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class BacktrackingUtilsTest {

    // --- findFirstSolution tests ---

    @Test
    void findFirstSolution_simpleSubsetSelection() {
        // Find a subset of [1,2,3,4] that sums to exactly 5
        var result = BacktrackingUtils.<Integer>findFirstSolution(
            state -> List.of(1, 2, 3, 4),           // candidates: always same pool
            (current, depth) -> {                      // isValid: sum <= 5
                int sum = current.stream().mapToInt(Integer::intValue).sum();
                return sum <= 5;
            },
            (current, depth) -> {                      // isComplete: sum == 5
                int sum = current.stream().mapToInt(Integer::intValue).sum();
                return sum == 5;
            },
            4  // maxDepth
        );

        assertTrue(result.isPresent());
        assertEquals(5, result.get().assignments().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void findFirstSolution_noSolutionExists() {
        // Find subset of [1,2] that sums to 10
        var result = BacktrackingUtils.<Integer>findFirstSolution(
            state -> List.of(1, 2),
            (current, depth) -> current.stream().mapToInt(Integer::intValue).sum() <= 10,
            (current, depth) -> current.stream().mapToInt(Integer::intValue).sum() == 10,
            5
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void findFirstSolution_emptyCandidates() {
        var result = BacktrackingUtils.<Integer>findFirstSolution(
            state -> List.of(),
            (current, depth) -> true,
            (current, depth) -> false,
            3
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void findFirstSolution_pruningReducesSearch() {
        // With strict pruning (sum <= 3), should not explore branches exceeding 3
        var result = BacktrackingUtils.<Integer>findFirstSolution(
            state -> List.of(1, 2, 3, 4, 5),
            (current, depth) -> current.stream().mapToInt(Integer::intValue).sum() <= 3,
            (current, depth) -> current.stream().mapToInt(Integer::intValue).sum() == 3,
            5
        );

        assertTrue(result.isPresent());
        assertEquals(3, result.get().assignments().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void findFirstSolution_maxDepthLimitsSearch() {
        // Max depth 1 with candidates that need depth 2
        var result = BacktrackingUtils.<Integer>findFirstSolution(
            state -> List.of(1, 2),
            (current, depth) -> true,
            (current, depth) -> current.size() == 2,
            1  // maxDepth too small
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void findFirstSolution_singleElementSolution() {
        var result = BacktrackingUtils.<Integer>findFirstSolution(
            state -> List.of(5, 3, 7),
            (current, depth) -> true,
            (current, depth) -> current.size() == 1 && current.get(0) == 5,
            3
        );

        assertTrue(result.isPresent());
        assertEquals(List.of(5), result.get().assignments());
    }

    // --- findAllSolutions tests ---

    @Test
    void findAllSolutions_findsMultipleSolutions() {
        // Find all subsets of [1,2,3] that sum to exactly 3
        var results = BacktrackingUtils.<Integer>findAllSolutions(
            state -> List.of(1, 2, 3),
            (current, depth) -> current.stream().mapToInt(Integer::intValue).sum() <= 3,
            (current, depth) -> current.stream().mapToInt(Integer::intValue).sum() == 3,
            3,
            0  // no limit
        );

        // Solutions: [1,2] and [3]
        assertEquals(2, results.size());
    }

    @Test
    void findAllSolutions_respectsMaxSolutions() {
        var results = BacktrackingUtils.<Integer>findAllSolutions(
            state -> List.of(1, 2, 3),
            (current, depth) -> true,
            (current, depth) -> current.size() == 2,
            3,
            1  // limit to 1
        );

        assertEquals(1, results.size());
    }

    @Test
    void findAllSolutions_noSolutions() {
        var results = BacktrackingUtils.<Integer>findAllSolutions(
            state -> List.of(1),
            (current, depth) -> current.stream().mapToInt(Integer::intValue).sum() <= 5,
            (current, depth) -> current.stream().mapToInt(Integer::intValue).sum() == 100,
            5,
            0
        );

        assertTrue(results.isEmpty());
    }

    // --- Set and Map integration tests ---

    @Test
    void findFirstSolution_usesSetForDedup() {
        // Simulate: pick unique items from candidates, no repeats allowed
        List<Integer> candidates = List.of(1, 2, 3);
        java.util.Set<Integer> used = new java.util.HashSet<>();

        var result = BacktrackingUtils.<Integer>findFirstSolution(
            state -> {
                // Return only candidates not yet used
                return candidates.stream()
                    .filter(c -> !used.contains(c))
                    .toList();
            },
            (current, depth) -> {
                // Each new element must not already be in current
                return current.stream().distinct().count() == current.size();
            },
            (current, depth) -> current.size() == 3,
            3
        );

        assertTrue(result.isPresent());
        assertEquals(3, result.get().assignments().size());
        assertEquals(3, result.get().assignments().stream().distinct().count());
    }

    @Test
    void findFirstSolution_usesMapForStateTracking() {
        // Simulate: track cumulative cost in a map
        java.util.Map<Integer, Integer> costMap = new java.util.HashMap<>();
        costMap.put(1, 10);
        costMap.put(2, 20);
        costMap.put(3, 30);

        int budget = 25;

        var result = BacktrackingUtils.<Integer>findFirstSolution(
            state -> List.of(1, 2, 3),
            (current, depth) -> {
                int totalCost = current.stream().mapToInt(costMap::get).sum();
                return totalCost <= budget;
            },
            (current, depth) -> {
                int totalCost = current.stream().mapToInt(costMap::get).sum();
                return totalCost == budget;
            },
            3
        );

        assertTrue(result.isPresent());
        int totalCost = result.get().assignments().stream().mapToInt(costMap::get).sum();
        assertEquals(budget, totalCost);
    }
}
