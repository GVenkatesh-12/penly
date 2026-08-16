package com.penly.editor.selection

import com.penly.core.geometry.Point
import com.penly.core.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LassoPathTest {
    private val square = LassoPath.of(points(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f))

    @Test
    fun rectanglePolygon_containsInsidePoint_excludesOutsidePoint() {
        assertTrue(square.contains(Point(50f, 50f)))
        assertTrue(square.contains(Point(0f, 0f)))
        assertTrue(square.contains(Point(99f, 99f)))
        assertFalse(square.contains(Point(150f, 50f)))
        assertFalse(square.contains(Point(50f, -10f)))
        assertFalse(square.contains(Point(-1f, 50f)))
    }

    @Test
    fun diagonalPolygon_containsPointsOnCorrectSide() {
        // Triangle: (0,0), (100,0), (0,100) — points inside the diagonal edge (x + y < 100).
        val triangle = LassoPath.of(points(0f, 0f, 100f, 0f, 0f, 100f))
        assertTrue(triangle.contains(Point(10f, 10f)))
        assertTrue(triangle.contains(Point(50f, 10f)))
        assertFalse(triangle.contains(Point(60f, 60f)))
        assertFalse(triangle.contains(Point(200f, 200f)))
    }

    @Test
    fun intersects_rectOverlappingPolygon() {
        val overlapping = Rect(50f, 50f, 150f, 150f)
        assertTrue(square.intersects(overlapping))
        // Polygon fully inside the rect: every polygon point is inside the rect.
        val containing = Rect(-50f, -50f, 200f, 200f)
        assertTrue(square.intersects(containing))
        // Rect fully inside the polygon: its corners are inside the polygon.
        val inside = Rect(25f, 25f, 75f, 75f)
        assertTrue(square.intersects(inside))
    }

    @Test
    fun intersects_rectCrossesPolygonEdge() {
        // A tall thin rect crossing the left edge of the square polygon (x=0)
        // without any corners inside the polygon or polygon points inside the rect.
        val crossingEdge = Rect(-10f, 40f, 10f, 60f)
        assertTrue(square.intersects(crossingEdge))

        // A wide thin rect crossing the top edge of the square polygon (y=0)
        val crossingTopEdge = Rect(40f, -10f, 60f, 10f)
        assertTrue(square.intersects(crossingTopEdge))
    }

    @Test
    fun intersects_rectDisjointFromPolygon() {
        assertFalse(square.intersects(Rect(150f, 150f, 200f, 200f)))
        assertFalse(square.intersects(Rect(-50f, -50f, -10f, -10f)))
        assertFalse(square.intersects(Rect(50f, 150f, 150f, 200f)))
    }

    @Test
    fun degeneratePaths_returnFalse() {
        val empty = LassoPath.of(emptyList())
        assertFalse(empty.contains(Point(0f, 0f)))
        assertFalse(empty.intersects(Rect(0f, 0f, 10f, 10f)))
        assertEquals(Rect(0f, 0f, 0f, 0f), empty.bounds)

        val onePoint = LassoPath.of(points(5f, 5f))
        assertFalse(onePoint.contains(Point(5f, 5f)))
        assertFalse(onePoint.intersects(Rect(0f, 0f, 10f, 10f)))

        val twoPoints = LassoPath.of(points(0f, 0f, 10f, 10f))
        assertFalse(twoPoints.contains(Point(5f, 5f)))
        assertFalse(twoPoints.intersects(Rect(0f, 0f, 20f, 20f)))
    }

    @Test
    fun bounds_minMaxOfPoints() {
        assertEquals(Rect(0f, 0f, 100f, 100f), square.bounds)
        val triangle = LassoPath.of(points(-5f, 3f, 7f, 3f, 1f, 20f))
        assertEquals(Rect(-5f, 3f, 7f, 20f), triangle.bounds)
    }

    private fun points(vararg values: Float): List<Point> = values.toList().chunked(2).map { (x, y) -> Point(x, y) }
}
