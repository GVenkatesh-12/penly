package com.penly.editor.selection

import com.penly.core.geometry.Point
import com.penly.core.geometry.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * A closed lasso polygon in page space, used to hit-test objects for selection.
 *
 * [contains] uses the classic ray-casting point-in-polygon test; [intersects] checks if any
 * rect corner is inside the polygon, any polygon point is inside the rect, or if any of their
 * edges cross. Paths with fewer than 3 points are degenerate: [contains] and [intersects]
 * return false and [bounds] is the min/max of the points.
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

    /** True when [rect] overlaps the polygon. */
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
        if (points.any { rect.contains(it) }) return true

        val rectPoints =
            listOf(
                Point(rect.left, rect.top),
                Point(rect.right, rect.top),
                Point(rect.right, rect.bottom),
                Point(rect.left, rect.bottom),
            )

        var j = points.size - 1
        for (i in points.indices) {
            val p1 = points[j]
            val p2 = points[i]
            for (k in 0..3) {
                val r1 = rectPoints[k]
                val r2 = rectPoints[(k + 1) % 4]
                if (segmentsIntersect(p1, p2, r1, r2)) return true
            }
            j = i
        }
        return false
    }

    /** True when the line segment from [p1] to [p2] intersects any polygon edge. */
    fun intersectsSegment(
        p1: Point,
        p2: Point,
    ): Boolean {
        if (points.size < 3) return false
        var j = points.size - 1
        for (i in points.indices) {
            if (segmentsIntersect(points[j], points[i], p1, p2)) return true
            j = i
        }
        return false
    }

    private fun segmentsIntersect(
        p1: Point,
        p2: Point,
        p3: Point,
        p4: Point,
    ): Boolean {
        val d1 = direction(p3, p4, p1)
        val d2 = direction(p3, p4, p2)
        val d3 = direction(p1, p2, p3)
        val d4 = direction(p1, p2, p4)

        val crossA =
            (d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)
        val crossB =
            (d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0)
        return crossA && crossB
    }

    private fun direction(
        a: Point,
        b: Point,
        c: Point,
    ): Float = (b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)

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
