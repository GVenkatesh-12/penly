package com.penly.editor.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoRedoStackTest {
    @Test
    fun pushUndo_returnsEntriesInLifoOrder() {
        val stack = UndoRedoStack<String>()
        stack.push("a")
        stack.push("b")
        stack.push("c")
        assertEquals("c", stack.undo())
        assertEquals("b", stack.undo())
        assertEquals("a", stack.undo())
        assertNull(stack.undo())
    }

    @Test
    fun undoThenRedo_returnsEntriesInOrder() {
        val stack = UndoRedoStack<String>()
        stack.push("a")
        stack.push("b")
        assertEquals("b", stack.undo())
        assertEquals("a", stack.undo())
        assertEquals("a", stack.redo())
        assertEquals("b", stack.redo())
        assertNull(stack.redo())
    }

    @Test
    fun newPushAfterUndo_clearsRedoStack() {
        val stack = UndoRedoStack<String>()
        stack.push("a")
        stack.push("b")
        assertEquals("b", stack.undo())
        assertTrue(stack.canRedo)
        stack.push("c")
        assertFalse(stack.canRedo)
        assertNull(stack.redo())
        // The undo history is now a, c.
        assertEquals("c", stack.undo())
        assertEquals("a", stack.undo())
        assertNull(stack.undo())
    }

    @Test
    fun emptyStack_undoAndRedoReturnNull() {
        val stack = UndoRedoStack<String>()
        assertNull(stack.undo())
        assertNull(stack.redo())
        assertFalse(stack.canUndo)
        assertFalse(stack.canRedo)
    }

    @Test
    fun capacity_evictsOldestEntries() {
        val stack = UndoRedoStack<String>(capacity = 100)
        for (index in 1..101) {
            stack.push("item-$index")
        }
        assertEquals("item-101", stack.undo())
        // 99 more undos reach the 100th entry; the very first push was evicted.
        var last = ""
        repeat(99) {
            last = stack.undo() ?: ""
        }
        assertEquals("item-2", last)
        assertNull(stack.undo())
    }

    @Test
    fun redoCapacity_respectsEvictionOrder() {
        val stack = UndoRedoStack<String>(capacity = 2)
        stack.push("a")
        stack.push("b")
        stack.push("c")
        assertEquals("c", stack.undo())
        assertEquals("b", stack.undo())
        assertEquals("b", stack.redo())
        assertEquals("c", stack.redo())
        assertNull(stack.redo())
    }

    @Test
    fun capacityOne_operatesCorrectly() {
        val stack = UndoRedoStack<String>(capacity = 1)
        stack.push("first")
        assertTrue(stack.canUndo)
        stack.push("second")
        assertEquals("second", stack.undo())
        assertNull(stack.undo())
        assertEquals("second", stack.redo())
        assertNull(stack.redo())
    }

    @Test
    fun interleavedUndoRedoPushes_maintainsCorrectHistory() {
        val stack = UndoRedoStack<Int>()
        stack.push(1)
        stack.push(2)
        assertEquals(2, stack.undo())
        stack.push(3)
        assertEquals(3, stack.undo())
        assertEquals(1, stack.undo())
        assertNull(stack.undo())
        assertEquals(1, stack.redo())
        assertEquals(3, stack.redo())
        assertNull(stack.redo())
    }
}
