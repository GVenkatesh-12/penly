package com.penly.editor.selection

import com.penly.core.geometry.Point
import com.penly.core.geometry.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * A closed lasso polygon in page space, used to hit-test objects for selection.
 *
 * [contains] uses the classic ray-casting point-in-polygon test; [intersects] implements the
 * Phase 3 contract "any rect corner inside the polygon OR any polygon point inside the rect"
 * (edge-crossing-only overlaps are reported as non-intersecting — an acceptable approximation
 * for v0.1 lasso selection). Paths with fewer than 3 points are degenerate: [contains] and
 * [intersects] return false and [bounds] is the min/max of the points.
 */
class LassoPath private constructor(
    private val points: List<Point>,
) {
    /** Min/max of the lasso points; `Rect(0f, 0f, 0f, 0f)` when the path is empty. */
    val bounds: Rect = computeBounds(points)

    /** True when [p] lies inside the polygon. Degenerate paths (<3 points) return false. */
    fun contains(p: Point): Boolean {
        if (points.size < 3) return false
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val current = points[i]
            val previous = points[j]
            if ((current.y > p.y) != (previous.y > p.y)) {
                val xIntersection =
                    (previous.x - current.x) * (p.y - current.y) / (previous.y - current.y) +
                        current.x
                if (p.x < xIntersection) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** True when [rect] overlaps the polygon: a corner inside the polygon or a point inside it. */
    fun intersects(rect: Rect): Boolean {
        if (points.size < 3 || rect.isEmpty) return false
        if (
            contains(Point(rect.left, rect.top)) ||
            contains(Point(rect.right, rect.top)) ||
            contains(Point(rect.left, rect.bottom)) ||
            contains(Point(rect.right, rect.bottom))
        ) {
            return true
        }
        return points.any { rect.contains(it) }
    }

    companion object {
        /** Builds a lasso from [points] (may be empty or degenerate). */
        fun of(points: List<Point>): LassoPath = LassoPath(points)
    }
}

private fun computeBounds(points: List<Point>): Rect {
    if (points.isEmpty()) return Rect(0f, 0f, 0f, 0f)
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (point in points) {
        minX = min(minX, point.x)
        minY = min(minY, point.y)
        maxX = max(maxX, point.x)
        maxY = max(maxY, point.y)
    }
    return Rect(minX, minY, maxX, maxY)
}
