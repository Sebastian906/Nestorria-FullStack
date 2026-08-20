package com.nestorria.server.common.util;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class UndoManagerTest {

    @Test
    void undoAll_reversesExecutionOrder() {
        List<String> log = new ArrayList<>();
        UndoManager undo = new UndoManager();
        undo.push(() -> log.add("undo-A"));
        undo.push(() -> log.add("undo-B"));
        undo.push(() -> log.add("undo-C"));

        undo.undoAll();

        assertEquals(List.of("undo-C", "undo-B", "undo-A"), log);
    }

    @Test
    void undo_singleStep_thenEmptyStackReturnsFalse() {
        List<String> log = new ArrayList<>();
        UndoManager undo = new UndoManager();
        undo.push(() -> log.add("undo-A"));

        assertTrue(undo.undo());
        assertEquals(List.of("undo-A"), log);
        assertFalse(undo.undo());
        assertEquals(0, undo.size());
    }

    @Test
    void undoAll_onEmptyStack_isNoOp() {
        UndoManager undo = new UndoManager();
        undo.undoAll();
        assertEquals(0, undo.size());
    }
}
