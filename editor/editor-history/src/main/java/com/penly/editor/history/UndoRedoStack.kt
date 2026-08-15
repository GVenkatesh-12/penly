package com.penly.editor.history

/**
 * A generic undo/redo stack of entries (typically editor commands).
 *
 * [push] records a new entry at the top of the undo stack and clears the redo stack (a new
 * action invalidates any undone state). Entries beyond [capacity] are evicted oldest-first so
 * the stack is bounded. [undo] pops the top entry onto the redo stack; [redo] moves it back.
 */
class UndoRedoStack<T>(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val undoItems = ArrayDeque<T>()
    private val redoItems = ArrayDeque<T>()

    val canUndo: Boolean
        get() = undoItems.isNotEmpty()

    val canRedo: Boolean
        get() = redoItems.isNotEmpty()

    fun push(entry: T) {
        redoItems.clear()
        undoItems.addLast(entry)
        while (undoItems.size > capacity) {
            undoItems.removeFirst()
        }
    }

    fun undo(): T? {
        if (!canUndo) return null
        val entry = undoItems.removeLast()
        redoItems.addLast(entry)
        return entry
    }

    fun redo(): T? {
        if (!canRedo) return null
        val entry = redoItems.removeLast()
        undoItems.addLast(entry)
        return entry
    }

    private companion object {
        const val DEFAULT_CAPACITY: Int = 100
    }
}
