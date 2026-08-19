package com.nestorria.server.common.algorithm;

import java.util.Map;
import java.util.Set;

/**
 * Deterministic finite automaton (AFD) for pattern recognition.
 * Recognition is O(n): one transition per input character, no backtracking.
 * States and transitions are modelled with {@link Set} and {@link Map}.
 * Educational utility (mirrors the role of Graph/SearchUtils in this package).
 * Not wired into production flows: the domain has no pattern-recognition need
 * that justifies replacing Jakarta validation (@Pattern/@Email) or DB search.
 */
public record FiniteAutomaton(
        int startState,
        Set<Integer> acceptStates,
        Map<Integer, Map<Character, Integer>> transitions) {

    public boolean accepts(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        int current = startState;
        for (int i = 0; i < input.length(); i++) {
            Map<Character, Integer> next = transitions.get(current);
            if (next == null) {
                return false;
            }
            Integer target = next.get(input.charAt(i));
            if (target == null) {
                return false;
            }
            current = target;
        }
        return acceptStates.contains(current);
    }
}
