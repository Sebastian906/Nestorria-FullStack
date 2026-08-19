package com.nestorria.server.common.algorithm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FiniteAutomatonTest {

    /** AFD para el slug del dominio: ^[a-z0-9]+(?:-[a-z0-9]+)*$ */
    private static FiniteAutomaton slugAutomaton() {
        // 0=start, 1=alnum, 2=tras-guion (requiere un alnum después)
        Map<Integer, Map<Character, Integer>> t = new HashMap<>();
        Map<Character, Integer> d0 = new HashMap<>(); alnum(d0, 1);
        Map<Character, Integer> d1 = new HashMap<>(); alnum(d1, 1); d1.put('-', 2);
        Map<Character, Integer> d2 = new HashMap<>(); alnum(d2, 1);
        t.put(0, d0); t.put(1, d1); t.put(2, d2);
        return new FiniteAutomaton(0, Set.of(1), t);
    }

    private static void alnum(Map<Character, Integer> m, int target) {
        for (char c = 'a'; c <= 'z'; c++) m.put(c, target);
        for (char c = '0'; c <= '9'; c++) m.put(c, target);
    }

    @Test
    void acceptsValidSlugs() {
        FiniteAutomaton a = slugAutomaton();
        assertTrue(a.accepts("casa"));
        assertTrue(a.accepts("apartamento-2b"));
    }

    @Test
    void rejectsInvalidSlugs() {
        FiniteAutomaton a = slugAutomaton();
        assertFalse(a.accepts("Apartamento"));
        assertFalse(a.accepts("casa-"));
        assertFalse(a.accepts("-casa"));
        assertFalse(a.accepts("casa--2"));
        assertFalse(a.accepts("mi casa"));
        assertFalse(a.accepts("casañ"));
    }

    @Test
    void rejectsNullAndEmptyWithoutNpe() {
        FiniteAutomaton a = slugAutomaton();
        assertFalse(a.accepts(null));
        assertFalse(a.accepts(""));
    }

    @Test
    void yearRangeIsActuallyEnforced() {
        // Y1 solo acepta '2' (años 2000-2999) en lugar de 0-9
        Map<Integer, Map<Character, Integer>> t = new HashMap<>();
        Map<Character, Integer> y1 = new HashMap<>();
        y1.put('2', 1);
        t.put(0, y1);
        FiniteAutomaton a = new FiniteAutomaton(0, Set.of(1), t);
        assertTrue(a.accepts("2"));
        assertFalse(a.accepts("9"));
    }
}
