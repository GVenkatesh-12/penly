package com.penly.core.geometry

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * An axis-aligned rectangle in float precision, defined by its left/top/right/bottom edges.
 *
 * [contains] is half-open: the left and top edges are inclusive, the right and bottom edges are
 * exclusive, matching `android.graphics.Rect` semantics.
 */
@Serializable
data class Rect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left

    val height: Float get() = bottom - top

    val isEmpty: Boolean get() = right <= left || bottom <= top

    fun contains(p: Point): Boolean = contains(p.x, p.y)

    fun contains(
        x: Float,
        y: Float,
    ): Boolean = x >= left && x < right && y >= top && y < bottom

    /**
     * Returns the smallest rectangle enclosing both this rectangle and [other].
     *
     * Follows `android.graphics.Rect.union` semantics: an empty rectangle contributes nothing, so
     * the result is the other rectangle when this one is empty, and vice versa.
     */
    fun union(other: Rect): Rect {
        if (isEmpty) return other
        if (other.isEmpty) return this
        return Rect(
            left = min(left, other.left),
            top = min(top, other.top),
            right = max(right, other.right),
            bottom = max(bottom, other.bottom),
        )
    }

    /** Returns a copy of this rectangle shrunk by [d] on every edge (negative [d] grows it). */
    fun inset(d: Float): Rect = Rect(left + d, top + d, right - d, bottom - d)

    /** Returns a copy of this rectangle shifted by [dx] along X and [dy] along Y. */
    fun translate(
        dx: Float,
        dy: Float,
    ): Rect = Rect(left + dx, top + dy, right + dx, bottom + dy)

    val center: Point get() = Point((left + right) / 2f, (top + bottom) / 2f)
}
