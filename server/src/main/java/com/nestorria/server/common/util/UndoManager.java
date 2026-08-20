package com.nestorria.server.common.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Pila LIFO de acciones de compensación (ArrayDeque: push/pop O(1)).
 * Diseñada para recursos EXTERNOS (Cloudinary, email, APIs) que una
 * transacción PostgreSQL no puede deshacer. push registra la acción,
 * undo() deshace la última, undoAll() deshace todas en orden inverso.
 */
public class UndoManager {

    @FunctionalInterface
    public interface UndoableAction {
        void undo();
    }

    private final Deque<UndoableAction> undoStack = new ArrayDeque<>();

    public void push(UndoableAction action) {
        undoStack.push(action);
    }

    // Deshace la última acción. false si la pila está vacía.
    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        undoStack.pop().undo();
        return true;
    }

    // Deshace todas las acciones en orden LIFO (inverso al registro).
    public void undoAll() {
        while (undo()) {
            // no-op: undo() ya avanza la pila
        }
    }

    public int size() {
        return undoStack.size();
    }
}
