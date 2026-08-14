package com.penly.core.ink

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasViewportTest {
    @Test
    fun initialViewport_isIdentity() {
        val viewport = CanvasViewport.INITIAL
        assertEquals(1f, viewport.scale)
        assertEquals(0f, viewport.offsetX)
        assertEquals(0f, viewport.offsetY)
    }

    @Test
    fun pageToScreen_roundTripsThroughScreenToPage() {
        val viewport = CanvasViewport(scale = 2f, offsetX = 40f, offsetY = -10f)
        val screenX = viewport.pageToScreenX(123f)
        val screenY = viewport.pageToScreenY(456f)
        assertEquals(123f, viewport.screenToPageX(screenX), 1e-3f)
        assertEquals(456f, viewport.screenToPageY(screenY), 1e-3f)
    }

    @Test
    fun pan_shiftsOffsetsByDelta() {
        val moved = CanvasViewport.INITIAL.pan(12f, -7f)
        assertEquals(12f, moved.offsetX)
        assertEquals(-7f, moved.offsetY)
    }

    @Test
    fun zoomAt_keepsFocusPointFixed() {
        val viewport = CanvasViewport.INITIAL
        val zoomed = viewport.zoomAt(100f, 50f, factor = 2f)
        assertEquals(2f, zoomed.scale)
        assertEquals(100f, zoomed.pageToScreenX(100f), 1e-3f)
        assertEquals(50f, zoomed.pageToScreenY(50f), 1e-3f)
    }

    @Test
    fun zoomAt_clampsToMinimumScale() {
        val zoomed = CanvasViewport.INITIAL.zoomAt(0f, 0f, factor = 0.001f)
        assertEquals(CanvasViewport.MIN_SCALE, zoomed.scale)
    }

    @Test
    fun zoomAt_clampsToMaximumScale() {
        val zoomed = CanvasViewport.INITIAL.zoomAt(0f, 0f, factor = 1000f)
        assertEquals(CanvasViewport.MAX_SCALE, zoomed.scale)
    }

    @Test
    fun zoomOut_keepsFocusPointFixed() {
        val viewport = CanvasViewport(scale = 4f, offsetX = 80f, offsetY = 0f)
        val zoomed = viewport.zoomAt(200f, 0f, factor = 0.5f)
        assertEquals(2f, zoomed.scale)
        assertEquals(880f, zoomed.pageToScreenX(200f), 1e-3f)
        assertEquals(0f, zoomed.pageToScreenY(0f), 1e-3f)
    }
}
