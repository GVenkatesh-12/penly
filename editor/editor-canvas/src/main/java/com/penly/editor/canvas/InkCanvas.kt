package com.penly.editor.canvas

import android.graphics.DashPathEffect
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
                    state.setCanvasSize(
                        Size(size.width.toFloat(), size.height.toFloat()),
                    )
                }.inkInput(state),
        onDraw = {
            drawRect(color = Color.White)
            val matrix = Matrix()
            matrix.setScale(
                state.viewport.scale,
                state.viewport.scale,
            )
            matrix.postTranslate(
                state.viewport.offsetX,
                state.viewport.offsetY,
            )
            state.currentTick
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                nativeCanvas.save()
                nativeCanvas.concat(matrix)
                try {
                    // Finished ink: per-object transform composed
                    // with the viewport for correct rendering of
                    // moved strokes. In-progress stroke stays on
                    // the lowest-latency active stroke path.
                    for (record in state.strokes) {
                        if (record.transform == Transform.IDENTITY) {
                            renderer.draw(
                                nativeCanvas,
                                record.stroke,
                                matrix,
                            )
                        } else {
                            val composed =
                                composeTransform(
                                    matrix,
                                    record.transform,
                                )
                            val objMatrix =
                                transformToMatrix(record.transform)
                            nativeCanvas.save()
                            nativeCanvas.concat(objMatrix)
                            renderer.draw(
                                nativeCanvas,
                                record.stroke,
                                composed,
                            )
                            nativeCanvas.restore()
                        }
                    }
                    state.inProgressStroke?.let { stroke ->
                        renderer.draw(nativeCanvas, stroke, matrix)
                    }
                    // Non-ink objects in page space, under the
                    // same viewport matrix.
                    for (obj in state.objects) {
                        drawObject(nativeCanvas, state, obj)
                    }
                    // Lasso overlay in page space (already
                    // stored as page coords).
                    drawLassoOverlay(nativeCanvas, state)
                } catch (exception: RuntimeException) {
                    Log.w(TAG, "page rendering failed", exception)
                } finally {
                    nativeCanvas.restore()
                }
                // Selection bounds in screen space.
                drawSelectionBounds(nativeCanvas, state)
            }
        },
    )
}

private const val TAG: String = "InkCanvas"

// ----- Pre-allocated paints (avoid per-frame allocation) -----

private val LASSO_STROKE_PAINT =
    Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF2196F3.toInt()
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
    }

private val LASSO_FILL_PAINT =
    Paint().apply {
        style = Paint.Style.FILL
        color = 0x1A2196F3
        isAntiAlias = true
    }

private val SELECTION_RECT_PAINT =
    Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF2196F3.toInt()
        isAntiAlias = true
    }

private val SELECTION_HANDLE_FILL =
    Paint().apply {
        style = Paint.Style.FILL
        color = 0xFF2196F3.toInt()
        isAntiAlias = true
    }

private val SELECTION_HANDLE_BORDER =
    Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0xFFFFFFFF.toInt()
        isAntiAlias = true
    }

private val IMAGE_PLACEHOLDER_PAINT =
    Paint().apply {
        style = Paint.Style.FILL
        color = 0x1A9E9E9E
        isAntiAlias = true
    }

private val IMAGE_PLACEHOLDER_BORDER =
    Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x559E9E9E.toInt()
        isAntiAlias = true
    }

private val IMAGE_SELECTED_PAINT =
    Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0xFF2196F3.toInt()
        isAntiAlias = true
    }

private val TEXT_PAINT =
    Paint().apply {
        isAntiAlias = true
    }

private const val HANDLE_RADIUS: Float = 5f

// ------ Transform helpers ------

/**
 * Viewport matrix composed with an object transform:
 * `viewport * (T * R * S)`.
 */
private fun composeTransform(
    viewportMatrix: Matrix,
    transform: Transform,
): Matrix {
    val objMatrix = transformToMatrix(transform)
    return Matrix(viewportMatrix).apply {
        preConcat(objMatrix)
    }
}

private fun transformToMatrix(transform: Transform): Matrix =
    Matrix().apply {
        setScale(transform.scaleX, transform.scaleY)
        postRotate(transform.rotationDegrees)
        postTranslate(
            transform.translationX,
            transform.translationY,
        )
    }

// ------ Object rendering ------

private fun drawObject(
    nativeCanvas: NativeCanvas,
    state: InkCanvasState,
    obj: PageObject,
) {
    val isSelected = state.isSelected(obj.objectId)
    when (obj) {
        is TextObject -> {
            TEXT_PAINT.textSize = obj.fontSize
            TEXT_PAINT.color = obj.colorArgb
            val baseline =
                obj.bounds.top - TEXT_PAINT.fontMetrics.ascent
            nativeCanvas.drawText(
                obj.text,
                obj.bounds.left,
                baseline,
                TEXT_PAINT,
            )
            if (isSelected) {
                nativeCanvas.drawRect(
                    obj.bounds.left,
                    obj.bounds.top,
                    obj.bounds.right,
                    obj.bounds.bottom,
                    IMAGE_SELECTED_PAINT,
                )
            }
        }
        is ImageObject -> {
            val bitmap = state.images[obj.objectId]
            val bounds = obj.bounds
            if (bitmap != null) {
                val rect =
                    AndroidRect(
                        bounds.left.toInt(),
                        bounds.top.toInt(),
                        bounds.right.toInt(),
                        bounds.bottom.toInt(),
                    )
                nativeCanvas.drawBitmap(bitmap, null, rect, null)
            } else {
                // Placeholder for images still loading.
                nativeCanvas.drawRect(
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom,
                    IMAGE_PLACEHOLDER_PAINT,
                )
                nativeCanvas.drawRect(
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom,
                    IMAGE_PLACEHOLDER_BORDER,
                )
            }
            if (isSelected) {
                nativeCanvas.drawRect(
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom,
                    IMAGE_SELECTED_PAINT,
                )
            }
        }
        else -> Unit
    }
}

// ------ Lasso + selection overlay ------

/**
 * Draws the lasso polyline in page space: a closed dashed
 * stroke with a translucent fill so the user sees the
 * enclosed region while dragging.
 */
private fun drawLassoOverlay(
    nativeCanvas: NativeCanvas,
    state: InkCanvasState,
) {
    if (state.lassoPoints.isEmpty()) return
    val path = Path()
    state.lassoPoints.forEachIndexed { index, point ->
        if (index == 0) {
            path.moveTo(point.x, point.y)
        } else {
            path.lineTo(point.x, point.y)
        }
    }
    path.close()
    // Scale the dash intervals and stroke width inversely
    // with viewport scale so the lasso looks consistent
    // regardless of zoom level.
    val invScale = 1f / state.viewport.scale
    LASSO_STROKE_PAINT.strokeWidth = 2f * invScale
    LASSO_STROKE_PAINT.pathEffect =
        DashPathEffect(
            floatArrayOf(10f * invScale, 6f * invScale),
            0f,
        )
    nativeCanvas.drawPath(path, LASSO_FILL_PAINT)
    nativeCanvas.drawPath(path, LASSO_STROKE_PAINT)
}

/**
 * Draws the selection bounding rectangle and 4-corner resize
 * handles in screen space (manual viewport conversion).
 */
private fun drawSelectionBounds(
    nativeCanvas: NativeCanvas,
    state: InkCanvasState,
) {
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
        SELECTION_RECT_PAINT,
    )
    // 4-corner handles.
    for ((hx, hy) in cornerHandles(left, top, right, bottom)) {
        nativeCanvas.drawCircle(
            hx,
            hy,
            HANDLE_RADIUS,
            SELECTION_HANDLE_FILL,
        )
        nativeCanvas.drawCircle(
            hx,
            hy,
            HANDLE_RADIUS,
            SELECTION_HANDLE_BORDER,
        )
    }
}

private fun cornerHandles(
    l: Float,
    t: Float,
    r: Float,
    b: Float,
): List<Pair<Float, Float>> = listOf(l to t, r to t, l to b, r to b)

// ------ Gesture routing ------

/**
 * Routes one gesture. Draw/erase mode feeds
 * [InkInputHandler]; selection mode pans/zooms with two
 * pointers, moves the selection with one pointer inside it,
 * taps an object to select it, or lasso-selects with one
 * pointer outside it. Double-tap resets the viewport.
 */
private fun Modifier.inkInput(state: InkCanvasState): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val down =
                awaitFirstDown(requireUnconsumed = false)
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
    var wasMultiTouch = false

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
                if (!wasMultiTouch) {
                    // First multi-touch frame: initialise
                    // baseline but skip zoom/pan to avoid
                    // the initial jitter.
                    wasMultiTouch = true
                } else if (span > 0f) {
                    state.zoomAt(
                        newCentroid.x,
                        newCentroid.y,
                        newSpan / span,
                    )
                    state.pan(
                        newCentroid.x - centroid.x,
                        newCentroid.y - centroid.y,
                    )
                }
                centroid = newCentroid
                span = newSpan
                pressed.forEach { it.consume() }
            }
            pressed.size == 1 -> {
                val change = pressed.first()
                if (change.id == activePointerId) {
                    if (!strokeStarted && !wasMultiTouch) {
                        startDraw(change)
                    }
                    if (strokeStarted) {
                        handler.onMove(
                            change.position,
                            change.uptimeMillis,
                            change.pressure,
                            change.type,
                        )
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
    handler.onUp()
}

private enum class SelectionGesture { MOVE, LASSO }

/**
 * Minimum movement to distinguish a drag from a tap (px).
 */
private const val TAP_SLOP: Float = 12f

/**
 * Maximum duration for a tap gesture (ms).
 */
private const val TAP_TIMEOUT: Long = 200L

private suspend fun AwaitPointerEventScope.handleSelectionGesture(
    state: InkCanvasState,
    down: PointerInputChange,
) {
    val activePointerId = down.id
    val downPos = down.position
    val downTime = down.uptimeMillis
    val downPage = state.viewport.screenToPage(downPos)

    // Decide initial mode: tap inside selection → move;
    // tap on an object → select it & move; otherwise lasso.
    val hitSelection =
        state.hitTestSelection(downPos.x, downPos.y)
    val hitObject =
        if (!hitSelection) {
            state.selectObjectAt(downPage.x, downPage.y)
        } else {
            null
        }
    val mode =
        if (hitSelection || hitObject != null) {
            SelectionGesture.MOVE
        } else {
            SelectionGesture.LASSO
        }

    var centroid = downPos
    var span = 0f
    var lastPageX = downPage.x
    var lastPageY = downPage.y
    var totalDx = 0f
    var totalDy = 0f
    var maxDist = 0f
    var wasMultiTouch = false

    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        when {
            pressed.size >= 2 -> {
                val newCentroid = centroidOf(pressed)
                val newSpan = spanOf(pressed)
                if (!wasMultiTouch) {
                    wasMultiTouch = true
                } else if (span > 0f) {
                    state.zoomAt(
                        newCentroid.x,
                        newCentroid.y,
                        newSpan / span,
                    )
                    state.pan(
                        newCentroid.x - centroid.x,
                        newCentroid.y - centroid.y,
                    )
                }
                centroid = newCentroid
                span = newSpan
                // Viewport changed; reset move baseline.
                val pageNow =
                    state.viewport.screenToPage(newCentroid)
                lastPageX = pageNow.x
                lastPageY = pageNow.y
                pressed.forEach { it.consume() }
            }
            pressed.size == 1 -> {
                val change = pressed.first()
                if (change.id == activePointerId) {
                    val dx = change.position.x - downPos.x
                    val dy = change.position.y - downPos.y
                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (dist > maxDist) maxDist = dist
                    when (mode) {
                        SelectionGesture.MOVE -> {
                            val pageNow =
                                state.viewport.screenToPage(
                                    change.position,
                                )
                            val dx = pageNow.x - lastPageX
                            val dy = pageNow.y - lastPageY
                            state.moveSelection(dx, dy)
                            totalDx += dx
                            totalDy += dy
                            lastPageX = pageNow.x
                            lastPageY = pageNow.y
                        }
                        SelectionGesture.LASSO -> {
                            state.addLassoPoint(
                                change.position.x,
                                change.position.y,
                            )
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
    val elapsed = System.currentTimeMillis() - downTime
    val isTap = maxDist < TAP_SLOP && elapsed < TAP_TIMEOUT
    when (mode) {
        SelectionGesture.MOVE -> {
            if (isTap) {
                // Tap inside selection without dragging —
                // keep selection as-is (no commit needed).
            } else {
                state.commitMove(totalDx, totalDy)
            }
        }
        SelectionGesture.LASSO -> {
            if (isTap) {
                // Tap outside selection = deselect.
                state.lassoPoints = emptyList()
                state.clearSelectionPublic()
            } else {
                // Lasso points are already in page space.
                state.selectLasso(state.lassoPoints)
            }
        }
    }
}
