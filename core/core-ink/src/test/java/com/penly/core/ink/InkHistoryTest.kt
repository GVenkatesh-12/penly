package com.penly.core.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkHistoryTest {
    @Test
    fun add_thenUndo_returnsLastItem() {
        val history = InkHistory<String>()
        history.add("a")
        history.add("b")
        assertEquals("b", history.undo())
        assertEquals("a", history.undo())
    }

    @Test
    fun undo_thenRedo_returnsItemInOrder() {
        val history = InkHistory<String>()
        history.add("a")
        history.add("b")
        history.undo()
        assertEquals("b", history.redo())
        assertNull(history.redo())
    }

    @Test
    fun newAddAfterUndo_clearsRedoStack() {
        val history = InkHistory<String>()
        history.add("a")
        history.add("b")
        history.undo()
        history.add("c")
        assertFalse(history.canRedo)
        assertEquals("c", history.undo())
        assertEquals("a", history.undo())
    }

    @Test
    fun clear_resetsBothStacks() {
        val history = InkHistory<String>()
        history.add("a")
        history.undo()
        history.clear()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(0, history.itemCount)
    }

    @Test
    fun undoOnEmpty_returnsNull() {
        assertNull(InkHistory<String>().undo())
    }

    @Test
    fun capacity_dropsOldestItems() {
        val history = InkHistory<String>(maxItems = 2)
        history.add("a")
        history.add("b")
        history.add("c")
        assertEquals(2, history.itemCount)
        assertEquals("c", history.undo())
        assertEquals("b", history.undo())
        assertNull(history.undo())
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
    }
}
