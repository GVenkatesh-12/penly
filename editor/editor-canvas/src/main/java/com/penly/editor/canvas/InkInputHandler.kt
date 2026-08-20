package com.penly.editor.canvas

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.StrokeInput
import com.penly.core.ink.CanvasViewport
import com.penly.core.ink.InputSanitizer
import com.penly.core.ink.PenTool
import com.penly.core.ink.StrokeRecord
import com.penly.core.model.PageObject
import kotlin.math.hypot

internal class InkInputHandler(
    private val state: InkCanvasState,
) {
    private val sanitizer = InputSanitizer()
    private val bounds = RectF()
    private val input = StrokeInput()
    private var activeTool: PenTool = PenTool.ERASER
    private var strokeToolType: InputToolType = InputToolType.UNKNOWN
    private var strokeStartMillis: Long = 0L

    private val erasedStrokes = mutableListOf<Pair<Int, StrokeRecord>>()
    private val erasedObjects = mutableListOf<Pair<Int, PageObject>>()

    fun onDown(
        position: Offset,
        timeMillis: Long,
        pressure: Float,
        type: PointerType,
    ) {
        if (position.isInvalid()) return
        sanitizer.reset()
        activeTool = state.tool
        strokeToolType = type.toInkToolType()
        strokeStartMillis = timeMillis
        bounds.setEmpty()
        val page = state.viewport.screenToPage(position)
        sanitizer.accept(page.x, page.y, 0L)
        if (activeTool.isStrokeTool) {
            state.startStroke(
                activeTool,
                strokeInput(page, 0L, pressure),
                bounds,
            )
        } else {
            erasedStrokes.clear()
            erasedObjects.clear()
            val (strokeHit, objectHit) = state.eraseImmediately(page.x, page.y, eraseRadius())
            strokeHit?.let { erasedStrokes += it }
            objectHit?.let { erasedObjects += it }
        }
    }

    fun onMove(
        position: Offset,
        timeMillis: Long,
        pressure: Float,
        type: PointerType,
    ) {
        if (position.isInvalid()) return
        val page = state.viewport.screenToPage(position)
        val elapsed = timeMillis - strokeStartMillis
        if (activeTool.isStrokeTool) {
            if (sanitizer.accept(page.x, page.y, elapsed)) {
                state.addInput(strokeInput(page, elapsed, pressure), bounds)
            }
        } else {
            val (strokeHit, objectHit) = state.eraseImmediately(page.x, page.y, eraseRadius())
            strokeHit?.let { erasedStrokes += it }
            objectHit?.let { erasedObjects += it }
        }
    }

    fun onUp() {
        if (activeTool.isStrokeTool) {
            state.endStroke(bounds)
        } else {
            state.commitEraseGesture(erasedStrokes, erasedObjects)
        }
    }

    fun abortStroke() {
        if (activeTool.isStrokeTool) {
            state.abortStroke()
        } else {
            state.abortEraseGesture(erasedStrokes, erasedObjects)
        }
        // An aborted gesture must leave nothing to commit: otherwise a pinch-interrupt during
        // an erase would restore the items but still push a delete command on up, and the next
        // undo would duplicate the restored strokes.
        erasedStrokes.clear()
        erasedObjects.clear()
    }

    private fun strokeInput(
        page: Offset,
        elapsedMillis: Long,
        pressure: Float,
    ): StrokeInput {
        input.update(
            x = page.x,
            y = page.y,
            elapsedTimeMillis = elapsedMillis,
            toolType = strokeToolType,
            pressure = if (pressure.isFinite() && pressure > 0f) pressure else 1f,
        )
        return input
    }

    private fun eraseRadius(): Float = ERASE_RADIUS_PX / state.viewport.scale

    private fun Offset.isInvalid(): Boolean = x.isNaN() || y.isNaN()

    private companion object {
        const val ERASE_RADIUS_PX: Float = 16f
    }
}

internal fun PointerType.toInkToolType(): InputToolType =
    when (this) {
        PointerType.Stylus,
        PointerType.Eraser,
        -> InputToolType.STYLUS

        PointerType.Touch -> InputToolType.TOUCH
        PointerType.Mouse -> InputToolType.MOUSE
        else -> InputToolType.UNKNOWN
    }

internal fun CanvasViewport.screenToPage(position: Offset): Offset =
    Offset(
        screenToPageX(position.x),
        screenToPageY(position.y),
    )

internal fun centroidOf(positions: Collection<Offset>): Offset {
    if (positions.isEmpty()) return Offset.Zero
    return positions.fold(Offset.Zero) { acc, position -> acc + position } / positions.size.toFloat()
}

internal fun spanOf(positions: Collection<Offset>): Float {
    if (positions.size < 2) return 0f
    val first = positions.first()
    val second = positions.last()
    return hypot(
        (second.x - first.x).toDouble(),
        (second.y - first.y).toDouble(),
    ).toFloat()
}
