package com.penly.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RectTest {
    private val rect = Rect(0f, 0f, 10f, 10f)

    @Test
    fun widthAndHeight_areEdgeDeltas() {
        assertEquals(10f, rect.width, 0f)
        assertEquals(10f, rect.height, 0f)
    }

    @Test
    fun isEmpty_requiresPositiveArea() {
        assertFalse(rect.isEmpty)
        assertTrue(Rect(5f, 5f, 5f, 5f).isEmpty)
        assertTrue(Rect(0f, 0f, 0f, 0f).isEmpty)
        assertTrue(Rect(0f, 0f, -1f, 10f).isEmpty)
    }

    @Test
    fun contains_isHalfOpenOnRightAndBottomEdges() {
        assertTrue(rect.contains(Point(5f, 5f)))
        assertTrue(rect.contains(Point(0f, 0f)))
        assertFalse(rect.contains(Point(10f, 5f)))
        assertFalse(rect.contains(Point(5f, 10f)))
        assertFalse(rect.contains(Point(-1f, 5f)))
        assertFalse(rect.contains(Point(5f, -1f)))
        assertTrue(rect.contains(5f, 5f))
    }

    @Test
    fun union_enclosesBothRects() {
        val other = Rect(5f, 5f, 15f, 20f)
        assertEquals(Rect(0f, 0f, 15f, 20f), rect.union(other))
    }

    @Test
    fun union_emptyRectContributesNothing() {
        val empty = Rect(0f, 0f, 0f, 0f)
        assertEquals(rect, rect.union(empty))
        assertEquals(rect, empty.union(rect))
    }

    @Test
    fun inset_shrinksEveryEdge() {
        assertEquals(Rect(2f, 2f, 8f, 8f), rect.inset(2f))
    }

    @Test
    fun inset_negativeGrowsEveryEdge() {
        assertEquals(Rect(-1f, -1f, 11f, 11f), rect.inset(-1f))
    }

    @Test
    fun translate_shiftsAllEdges() {
        assertEquals(Rect(11f, 22f, 13f, 24f), Rect(1f, 2f, 3f, 4f).translate(10f, 20f))
    }

    @Test
    fun center_isMidpointOfEdges() {
        assertEquals(Point(5f, 5f), rect.center)
        assertEquals(Point(2f, 3f), Rect(0f, 0f, 4f, 6f).center)
    }
}
