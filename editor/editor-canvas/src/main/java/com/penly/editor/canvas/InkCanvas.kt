package com.penly.editor.canvas

import android.graphics.Matrix
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun rememberInkCanvasState(): InkCanvasState = remember { InkCanvasState() }

@Composable
fun inkCanvas(
    state: InkCanvasState,
    modifier: Modifier = Modifier,
) {
    val renderer = state.renderer
    Canvas(
        modifier = modifier.inkInput(state),
        onDraw = {
            drawRect(color = Color.White)
            val matrix = Matrix()
            matrix.setScale(state.viewport.scale, state.viewport.scale)
            matrix.postTranslate(state.viewport.offsetX, state.viewport.offsetY)
            state.currentTick
            drawIntoCanvas { canvas ->
                try {
                    for (record in state.strokes) {
                        renderer.draw(canvas.nativeCanvas, record.stroke, matrix)
                    }
                    state.inProgressStroke?.let { stroke ->
                        renderer.draw(canvas.nativeCanvas, stroke, matrix)
                    }
                } catch (exception: RuntimeException) {
                    Log.w(TAG, "stroke rendering failed", exception)
                }
            }
        },
    )
}

private const val TAG: String = "InkCanvas"

private fun Modifier.inkInput(state: InkCanvasState): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val handler = InkInputHandler(state)
            var activePointerId = down.id
            var strokeStarted = false
            var centroid = down.position
            var span = 0f

            fun startDraw(change: PointerInputChange) {
                handler.onDown(change.position, change.uptimeMillis, change.pressure, change.type)
                strokeStarted = true
            }

            startDraw(down)
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                when {
                    pressed.size >= 2 -> {
                        strokeStarted = false
                        handler.abortStroke()
                        val newCentroid = centroidOf(pressed)
                        val newSpan = spanOf(pressed)
                        if (span > 0f) {
                            state.zoomAt(newCentroid.x, newCentroid.y, newSpan / span)
                        }
                        state.pan(newCentroid.x - centroid.x, newCentroid.y - centroid.y)
                        centroid = newCentroid
                        span = newSpan
                        pressed.forEach { it.consume() }
                    }
                    pressed.size == 1 -> {
                        val change = pressed.first()
                        if (change.id == activePointerId) {
                            if (!strokeStarted) {
                                startDraw(change)
                            }
                            handler.onMove(
                                change.position,
                                change.uptimeMillis,
                                change.pressure,
                                change.type,
                            )
                            centroid = change.position
                            span = 0f
                        }
                    }
                    else -> break
                }
                if (event.changes.none { it.pressed }) {
                    break
                }
            }
            handler.onUp()
        }
    }
