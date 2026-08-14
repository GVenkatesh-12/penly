package com.penly.editor.canvas

import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import com.penly.core.ink.BrushFactory
import com.penly.core.ink.CanvasViewport
import com.penly.core.ink.InkHistory
import com.penly.core.ink.PenTool
import com.penly.core.ink.StrokeRecord

class InkCanvasState {
    val renderer: CanvasStrokeRenderer = CanvasStrokeRenderer.create()

    var viewport by mutableStateOf(CanvasViewport.INITIAL)
        private set

    var tool by mutableStateOf(PenTool.PEN)
        private set

    val strokes = mutableStateListOf<StrokeRecord>()

    var inProgressStroke by mutableStateOf<InProgressStroke?>(null)
        private set

    private val history = InkHistory<StrokeRecord>()
    private val drawTick = mutableIntStateOf(0)

    val canUndo: Boolean
        get() = history.canUndo

    val canRedo: Boolean
        get() = history.canRedo

    val currentTick: Int
        get() = drawTick.intValue

    fun selectTool(tool: PenTool) {
        this.tool = tool
    }

    fun pan(
        deltaX: Float,
        deltaY: Float,
    ) {
        viewport = viewport.pan(deltaX, deltaY)
    }

    fun zoomAt(
        focusX: Float,
        focusY: Float,
        factor: Float,
    ) {
        viewport = viewport.zoomAt(focusX, focusY, factor)
    }

    fun startStroke(
        tool: PenTool,
        firstInput: StrokeInput,
        bounds: RectF,
    ) {
        val stroke = InProgressStroke()
        stroke.start(BrushFactory.createBrush(tool, tool.defaultSize, tool.defaultColorArgb))
        stroke.enqueueInputs(MutableStrokeInputBatch().add(firstInput), EMPTY_BATCH)
        stroke.updateShape()
        inProgressStroke = stroke
        bounds.union(firstInput.x, firstInput.y)
        bumpTick()
    }

    fun addInput(
        input: StrokeInput,
        bounds: RectF,
    ) {
        val stroke = inProgressStroke ?: return
        stroke.enqueueInputs(MutableStrokeInputBatch().add(input), EMPTY_BATCH)
        stroke.updateShape()
        bounds.union(input.x, input.y)
        bumpTick()
    }

    fun endStroke(bounds: RectF) {
        val stroke = inProgressStroke ?: return
        stroke.finishInput()
        stroke.updateShape()
        val record = StrokeRecord(stroke.toImmutable(), RectF(bounds))
        history.add(record)
        strokes.add(record)
        inProgressStroke = null
        bumpTick()
    }

    fun abortStroke() {
        inProgressStroke = null
        bumpTick()
    }

    fun undo() {
        val record = history.undo() ?: return
        strokes.remove(record)
        bumpTick()
    }

    fun redo() {
        val record = history.redo() ?: return
        strokes.add(record)
        bumpTick()
    }

    fun clearAll() {
        strokes.clear()
        history.clear()
        inProgressStroke = null
        bumpTick()
    }

    fun eraseAt(
        pageX: Float,
        pageY: Float,
        radius: Float,
    ) {
        val hit =
            strokes.lastOrNull { record ->
                val bounds = RectF(record.bounds)
                bounds.inset(-radius, -radius)
                bounds.contains(pageX, pageY)
            }
        if (hit != null) {
            strokes.remove(hit)
            bumpTick()
        }
    }

    private fun bumpTick() {
        drawTick.intValue++
    }

    private companion object {
        val EMPTY_BATCH = MutableStrokeInputBatch()
    }
}
