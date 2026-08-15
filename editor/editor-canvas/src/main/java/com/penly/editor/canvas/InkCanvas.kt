package com.penly.editor.canvas

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.penly.core.geometry.Point
import com.penly.core.geometry.Transform
import com.penly.core.model.ImageObject
import com.penly.core.model.PageObject
import com.penly.core.model.TextObject
import android.graphics.Canvas as NativeCanvas
import android.graphics.Rect as AndroidRect

@Composable
fun rememberInkCanvasState(): InkCanvasState = remember { InkCanvasState() }

@Composable
fun inkCanvas(
    state: InkCanvasState,
    modifier: Modifier = Modifier,
) {
    val renderer = state.renderer
    Canvas(
        modifier =
            modifier
                .onSizeChanged { size ->
                    state.setCanvasSize(Size(size.width.toFloat(), size.height.toFloat()))
                }.inkInput(state),
        onDraw = {
            drawRect(color = Color.White)
            val matrix = Matrix()
            matrix.setScale(state.viewport.scale, state.viewport.scale)
            matrix.postTranslate(state.viewport.offsetX, state.viewport.offsetY)
            state.currentTick
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                nativeCanvas.save()
                nativeCanvas.concat(matrix)
                try {
                    // Finished ink (per-object transform composed with the viewport) and the
                    // in-progress stroke stay together: lowest-latency active stroke rendering.
                    for (record in state.strokes) {
                        if (record.transform == Transform.IDENTITY) {
                            renderer.draw(nativeCanvas, record.stroke, matrix)
                        } else {
                            val strokeMatrix = composeTransform(matrix, record.transform)
                            val objMatrix =
                                Matrix().apply {
                                    setScale(record.transform.scaleX, record.transform.scaleY)
                                    postRotate(record.transform.rotationDegrees)
                                    postTranslate(
                                        record.transform.translationX,
                                        record.transform.translationY,
                                    )
                                }
                            nativeCanvas.save()
                            nativeCanvas.concat(objMatrix)
                            renderer.draw(nativeCanvas, record.stroke, strokeMatrix)
                            nativeCanvas.restore()
                        }
                    }
                    state.inProgressStroke?.let { stroke ->
                        renderer.draw(nativeCanvas, stroke, matrix)
                    }
                    // Non-ink objects in page space, under the same viewport matrix.
                    for (obj in state.objects) {
                        drawObject(nativeCanvas, state, obj)
                    }
                } catch (exception: RuntimeException) {
                    Log.w(TAG, "page rendering failed", exception)
                } finally {
                    nativeCanvas.restore()
                }
                // Selection overlay in identity space (viewport math applied manually).
                drawSelectionOverlay(nativeCanvas, state)
            }
        },
    )
}

private const val TAG: String = "InkCanvas"

/** Viewport matrix composed with an object transform: `viewport * (T * R * S)`. */
private fun composeTransform(
    viewportMatrix: Matrix,
    transform: Transform,
): Matrix {
    val objMatrix =
        Matrix().apply {
            setScale(transform.scaleX, transform.scaleY)
            postRotate(transform.rotationDegrees)
            postTranslate(transform.translationX, transform.translationY)
        }
    return Matrix(viewportMatrix).apply {
        preConcat(objMatrix)
    }
}

private fun drawObject(
    nativeCanvas: NativeCanvas,
    state: InkCanvasState,
    obj: PageObject,
) {
    when (obj) {
        is TextObject -> {
            val paint =
                Paint().apply {
                    textSize = obj.fontSize
                    color = obj.colorArgb
                }
            val baseline = obj.bounds.top - paint.fontMetrics.ascent
            nativeCanvas.drawText(obj.text, obj.bounds.left, baseline, paint)
        }
        is ImageObject -> {
            val bitmap = state.images[obj.objectId] ?: return
            val bounds = obj.bounds
            val rect =
                AndroidRect(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt(),
                )
            nativeCanvas.drawBitmap(bitmap, null, rect, null)
        }
        else -> Unit
    }
}

private fun drawSelectionOverlay(
    nativeCanvas: NativeCanvas,
    state: InkCanvasState,
) {
    if (state.lassoPoints.isNotEmpty()) {
        val path = Path()
        state.lassoPoints.forEachIndexed { index, point ->
            if (index == 0) {
                path.moveTo(point.x, point.y)
            } else {
                path.lineTo(point.x, point.y)
            }
        }
        nativeCanvas.drawPath(
            path,
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = LASSO_COLOR
                isAntiAlias = true
            },
        )
    }
    val bounds = state.selectionBounds ?: return
    val left = state.viewport.pageToScreenX(bounds.left)
    val top = state.viewport.pageToScreenY(bounds.top)
    val right = state.viewport.pageToScreenX(bounds.right)
    val bottom = state.viewport.pageToScreenY(bounds.bottom)
    nativeCanvas.drawRect(
        left,
        top,
        right,
        bottom,
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = SELECTION_COLOR
            isAntiAlias = true
        },
    )
}

private const val LASSO_COLOR: Int = 0x802196F3.toInt()
private const val SELECTION_COLOR: Int = 0xFF2196F3.toInt()

/**
 * Routes one gesture. Draw/erase mode feeds [InkInputHandler]; selection mode pans/zooms with
 * two pointers, moves the selection with one pointer inside it, or lasso-selects with one
 * pointer outside it. The ink handler is never fed in selection mode.
 */
private fun Modifier.inkInput(state: InkCanvasState): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            if (state.selectionMode) {
                handleSelectionGesture(state, down)
            } else {
                handleDrawGesture(state, down)
            }
        }
    }

private suspend fun AwaitPointerEventScope.handleDrawGesture(
    state: InkCanvasState,
    down: PointerInputChange,
) {
    val handler = InkInputHandler(state)
    val activePointerId = down.id
    var strokeStarted = false
    var centroid = down.position
    var span = 0f

    fun startDraw(change: PointerInputChange) {
        handler.onDown(
            change.position,
            change.uptimeMillis,
            change.pressure,
            change.type,
        )
        strokeStarted = true
    }

    startDraw(down)
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        when {
            pressed.size >= 2 -> {
                if (strokeStarted) {
                    handler.abortStroke()
                    strokeStarted = false
                }
                val newCentroid = centroidOf(pressed)
                val newSpan = spanOf(pressed)
                if (span > 0f) {
                    state.zoomAt(
                        newCentroid.x,
                        newCentroid.y,
                        newSpan / span,
                    )
                }
                state.pan(
                    newCentroid.x - centroid.x,
                    newCentroid.y - centroid.y,
                )
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
                change.consume()
            }
            else -> break
        }
        if (event.changes.none { it.pressed }) {
            break
        }
    }
    handler.onUp()
}

private enum class SelectionGesture { MOVE, LASSO }

private suspend fun AwaitPointerEventScope.handleSelectionGesture(
    state: InkCanvasState,
    down: PointerInputChange,
) {
    val activePointerId = down.id
    val downPage = state.viewport.screenToPage(down.position)
    val mode =
        if (state.hitTestSelection(down.position.x, down.position.y)) {
            SelectionGesture.MOVE
        } else {
            SelectionGesture.LASSO
        }
    var centroid = down.position
    var span = 0f
    var lastPageX = downPage.x
    var lastPageY = downPage.y
    var totalDx = 0f
    var totalDy = 0f

    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        when {
            pressed.size >= 2 -> {
                val newCentroid = centroidOf(pressed)
                val newSpan = spanOf(pressed)
                if (span > 0f) {
                    state.zoomAt(
                        newCentroid.x,
                        newCentroid.y,
                        newSpan / span,
                    )
                }
                state.pan(
                    newCentroid.x - centroid.x,
                    newCentroid.y - centroid.y,
                )
                centroid = newCentroid
                span = newSpan
                // The viewport changed underneath us; reset the move baseline.
                val pageNow = state.viewport.screenToPage(newCentroid)
                lastPageX = pageNow.x
                lastPageY = pageNow.y
                pressed.forEach { it.consume() }
            }
            pressed.size == 1 -> {
                val change = pressed.first()
                if (change.id == activePointerId) {
                    when (mode) {
                        SelectionGesture.MOVE -> {
                            val pageNow = state.viewport.screenToPage(change.position)
                            val dx = pageNow.x - lastPageX
                            val dy = pageNow.y - lastPageY
                            state.moveSelection(dx, dy)
                            totalDx += dx
                            totalDy += dy
                            lastPageX = pageNow.x
                            lastPageY = pageNow.y
                        }
                        SelectionGesture.LASSO -> {
                            state.addLassoPoint(change.position)
                        }
                    }
                    centroid = change.position
                    span = 0f
                }
                change.consume()
            }
            else -> break
        }
        if (event.changes.none { it.pressed }) {
            break
        }
    }
    when (mode) {
        SelectionGesture.MOVE -> state.commitMove(totalDx, totalDy)
        SelectionGesture.LASSO -> {
            val pagePoints =
                state.lassoPoints.map { point ->
                    Point(
                        state.viewport.screenToPageX(point.x),
                        state.viewport.screenToPageY(point.y),
                    )
                }
            state.selectLasso(pagePoints)
        }
    }
}
