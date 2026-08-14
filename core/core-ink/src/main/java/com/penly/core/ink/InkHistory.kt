package com.penly.core.ink

class InkHistory<T>(
    private val maxItems: Int = MAX_ITEMS,
) {
    private val items = ArrayDeque<T>()
    private val redoItems = ArrayDeque<T>()

    val itemCount: Int
        get() = items.size

    val canUndo: Boolean
        get() = items.isNotEmpty()

    val canRedo: Boolean
        get() = redoItems.isNotEmpty()

    fun add(item: T) {
        redoItems.clear()
        items.addLast(item)
        while (items.size > maxItems) {
            items.removeFirst()
        }
    }

    fun undo(): T? {
        if (!canUndo) return null
        val item = items.removeLast()
        redoItems.addLast(item)
        return item
    }

    fun redo(): T? {
        if (!canRedo) return null
        val item = redoItems.removeLast()
        items.addLast(item)
        return item
    }

    fun clear() {
        items.clear()
        redoItems.clear()
    }

    private companion object {
        const val MAX_ITEMS: Int = 500
    }
}
