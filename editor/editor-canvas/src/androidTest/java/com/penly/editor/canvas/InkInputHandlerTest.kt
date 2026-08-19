package com.penly.editor.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.ink.brush.InputToolType
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.penly.core.geometry.Point
import com.penly.core.ink.PenTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gesture-level workflow tests for [InkInputHandler]: the mapping from raw pointer streams
 * to canvas mutations. This is the layer that decides whether a real stylus session draws,
 * erases, aborts cleanly, and survives multi-touch interrupts — the exact things manual
 * testers exercise by hand on a device.
 */
@RunWith(AndroidJUnit4::class)
class InkInputHandlerTest {
    @Test
    fun downMoveUp_commitsASingleStroke() {
        val state = InkCanvasState()
        val handler = InkInputHandler(state)

        handler.onDown(Offset(10f, 10f), timeMillis = 1000L, pressure = 0.5f, type = PointerType.Stylus)
        handler.onMove(Offset(30f, 30f), timeMillis = 1016L, pressure = 0.7f, type = PointerType.Stylus)
        handler.onMove(Offset(60f, 20f), timeMillis = 1032L, pressure = 0.9f, type = PointerType.Stylus)
        handler.onUp()

        assertEquals(1, state.strokes.size)
        val stroke = state.strokes.first().stroke
        assertEquals(3, stroke.inputs.size)
        assertEquals(InputToolType.STYLUS, stroke.inputs.get(0).toolType)
        assertTrue(state.canUndo)
    }

    @Test
    fun touchPointer_mapsToTouchToolType() {
        val state = InkCanvasState()
        val handler = InkInputHandler(state)

        handler.onDown(Offset(5f, 5f), timeMillis = 1000L, pressure = 1f, type = PointerType.Touch)
        handler.onMove(Offset(25f, 25f), timeMillis = 1016L, pressure = 1f, type = PointerType.Touch)
        handler.onUp()

        val toolType =
            state
                .strokes
                .first()
                .stroke
                .inputs
                .get(0)
                .toolType
        assertEquals(InputToolType.TOUCH, toolType)
    }

    @Test
    fun duplicateMoveAtSameTimeAndPosition_isRejectedBySanitizer() {
        val state = InkCanvasState()
        val handler = InkInputHandler(state)

        handler.onDown(Offset(10f, 10f), timeMillis = 1000L, pressure = 0.5f, type = PointerType.Stylus)
        handler.onMove(Offset(40f, 40f), timeMillis = 1016L, pressure = 0.5f, type = PointerType.Stylus)
        // Exact same timestamp + position must be dropped, not appended as a zero-length segment.
        handler.onMove(Offset(40f, 40f), timeMillis = 1016L, pressure = 0.5f, type = PointerType.Stylus)
        handler.onUp()

        val inputCount =
            state
                .strokes
                .first()
                .stroke
                .inputs
                .size
        assertEquals(2, inputCount)
    }

    @Test
    fun invalidNanPositions_areIgnored() {
        val state = InkCanvasState()
        val handler = InkInputHandler(state)

        handler.onDown(Offset(Float.NaN, Float.NaN), timeMillis = 1000L, pressure = 0.5f, type = PointerType.Stylus)
        handler.onMove(Offset(10f, 10f), timeMillis = 1016L, pressure = 0.5f, type = PointerType.Stylus)
        handler.onUp()

        assertEquals(0, state.strokes.size)
        assertTrue(!state.canUndo)
    }

    @Test
    fun abortDuringDraw_discardsTheInProgressStroke() {
        val state = InkCanvasState()
        val handler = InkInputHandler(state)

        handler.onDown(Offset(10f, 10f), timeMillis = 1000L, pressure = 0.5f, type = PointerType.Stylus)
        handler.onMove(Offset(50f, 50f), timeMillis = 1016L, pressure = 0.5f, type = PointerType.Stylus)
        // A two-finger pinch interrupt calls abortStroke; nothing may be committed.
        handler.abortStroke()
        handler.onUp()

        assertEquals(0, state.strokes.size)
        assertTrue(!state.canUndo)
    }

    @Test
    fun eraserGesture_removesHitStrokeAndRestoresOnAbort() {
        val state = InkCanvasState()
        addStrokeThroughHandler(state)

        val handler = InkInputHandler(state)
        state.selectTool(PenTool.ERASER)
        handler.onDown(Offset(10f, 10f), timeMillis = 2000L, pressure = 1f, type = PointerType.Stylus)
        handler.onMove(Offset(50f, 50f), timeMillis = 2016L, pressure = 1f, type = PointerType.Stylus)
        assertEquals(0, state.strokes.size)
        // Abort (e.g. pinch during erase) must restore everything erased so far.
        handler.abortStroke()
        assertEquals(1, state.strokes.size)

        // And a full gesture commits a single undoable deletion.
        handler.onDown(Offset(10f, 10f), timeMillis = 3000L, pressure = 1f, type = PointerType.Stylus)
        handler.onUp()
        assertEquals(0, state.strokes.size)
        state.undo()
        assertEquals(1, state.strokes.size)
    }

    @Test
    fun eraserGesture_hitsTextObjects() {
        val state = InkCanvasState()
        state.insertText("Hello", 20f, 0xFF000000.toInt(), Point(50f, 50f))
        assertEquals(1, state.objects.size)

        val handler = InkInputHandler(state)
        state.selectTool(PenTool.ERASER)
        handler.onDown(Offset(55f, 55f), timeMillis = 1000L, pressure = 1f, type = PointerType.Stylus)
        handler.onUp()

        assertEquals(0, state.objects.size)
        state.undo()
        assertEquals(1, state.objects.size)
    }

    @Test
    fun eraserRadius_scalesWithViewportZoom() {
        val state = InkCanvasState()
        // Zoom 2x centered at the origin: scale = 2, offset stays (0, 0).
        state.zoomAt(focusX = 0f, focusY = 0f, factor = 2f)
        assertEquals(2f, state.viewport.scale, 0f)
        addStrokeThroughHandler(state, from = Point(0f, 0f), to = Point(100f, 100f))

        val handler = InkInputHandler(state)
        state.selectTool(PenTool.ERASER)
        // Page-space radius is 16px / scale = 8px at 2x zoom.
        // Point 5.7px from the diagonal is inside the 8px radius: erased.
        handler.onDown(Offset(50f, 58f), timeMillis = 1000L, pressure = 1f, type = PointerType.Stylus)
        assertEquals(0, state.strokes.size)

        // Re-add and erase from screen (50, 80) -> page (25, 40), ~10.6px off the diagonal:
        // outside the scaled-down 8px radius. At scale 1 the 16px radius would have hit,
        // so this pins the scale division.
        addStrokeThroughHandler(state, from = Point(0f, 0f), to = Point(100f, 100f))
        val second = InkInputHandler(state)
        second.onDown(Offset(50f, 80f), timeMillis = 2000L, pressure = 1f, type = PointerType.Stylus)
        assertEquals(1, state.strokes.size)
    }

    private fun addStrokeThroughHandler(
        state: InkCanvasState,
        from: Point = Point(10f, 10f),
        to: Point = Point(40f, 40f),
    ) {
        val handler = InkInputHandler(state)
        handler.onDown(Offset(from.x, from.y), timeMillis = 1000L, pressure = 0.5f, type = PointerType.Stylus)
        handler.onMove(Offset(to.x, to.y), timeMillis = 1016L, pressure = 0.5f, type = PointerType.Stylus)
        handler.onUp()
    }
}
