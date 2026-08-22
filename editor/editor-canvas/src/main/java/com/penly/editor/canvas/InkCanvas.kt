package com.penly.editor.canvas

import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import com.penly.core.geometry.Rect
import com.penly.core.geometry.Transform
import com.penly.core.model.ImageObject
import com.penly.core.model.PageObject
import com.penly.core.model.TextObject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import android.graphics.Canvas as NativeCanvas

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
                }.inkInput(state)
                .testTag(INK_CANVAS_TAG),
        onDraw = {
            drawRect(color = Color.White)
            val matrix =
                transformToMatrix(
                    Transform(
                        translationX = state.viewport.offsetX,
                        translationY = state.viewport.offsetY,
                        scaleX = state.viewport.scale,
                        scaleY = state.viewport.scale,
                    ),
                )
            state.currentTick
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                try {
                    // All page content lives in page space; one concat maps it to screen
                    // pixels. CanvasStrokeRenderer.draw applies its matrix argument modulo
                    // translation only (androidx Ink contract), so strokes must be drawn
                    // with the translation already carried by the canvas matrix — otherwise
                    // ink ignores pan and zoom focus while objects (drawn under the same
                    // concat) follow the viewport, and hit-testing no longer matches pixels.
                    nativeCanvas.save()
                    nativeCanvas.concat(matrix)
                    try {
                        for (record in state.strokes) {
                            if (record.transform == Transform.IDENTITY) {
                                renderer.draw(nativeCanvas, record.stroke, matrix)
                            } else {
                                // Object transform composes on top of the viewport already
                                // concat'd above: the canvas gets viewport o object, while the
                                // renderer argument carries the full linear part
                                // (viewport scale x object scale) for rendering quality.
                                val objectMatrix = transformToMatrix(record.transform)
                                val combined =
                                    transformToMatrix(
                                        record.transform.throughViewport(
                                            state.viewport.scale,
                                            state.viewport.offsetX,
                                            state.viewport.offsetY,
                                        ),
                                    )
                                nativeCanvas.save()
                                nativeCanvas.concat(objectMatrix)
                                try {
                                    renderer.draw(nativeCanvas, record.stroke, combined)
                                } finally {
                                    nativeCanvas.restore()
                                }
                            }
                        }
                        state.inProgressStroke?.let { stroke ->
                            renderer.draw(nativeCanvas, stroke, matrix)
                        }
                        for (obj in state.objects) {
                            drawObject(nativeCanvas, state, obj)
                        }
                        drawLassoOverlay(nativeCanvas, state)
                    } finally {
                        nativeCanvas.restore()
                    }
                } catch (exception: RuntimeException) {
                    Log.w(TAG, "page rendering failed", exception)
                }
                drawSelectionBounds(nativeCanvas, state)
            }
        },
    )
}

private const val TAG: String = "InkCanvas"

/** Semantics tag on the ink surface; UI tests target this node for gestures. */
const val INK_CANVAS_TAG: String = "inkCanvas"

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
        pathEffect = DashPathEffect(floatArrayOf(8f, 5f), 0f)
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
        strokeWidth = 2f
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

private const val HANDLE_RADIUS: Float = 10f
private const val HANDLE_TOUCH_RADIUS_PX: Float = 64f
private const val MIN_SELECTION_SIZE: Float = 20f

// ------ Transform helpers ------

/**
 * Converts a [Transform] (`p' = t + R * S * p`) into the equivalent Android [Matrix]
 * (`M = T * R * S`), so `matrix.mapPoints(p)` matches [Transform.apply]. Android's
 * `post*` calls concatenate on the LEFT (`M' = new * M`), so the operations must be
 * applied in reverse order: scale, then rotate, then translate.
 */
internal fun transformToMatrix(transform: Transform): Matrix =
    Matrix().apply {
        setScale(transform.scaleX, transform.scaleY)
        postRotate(transform.rotationDegrees)
        postTranslate(transform.translationX, transform.translationY)
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
            val lines = obj.text.split('\n')
            var baseline = obj.bounds.top - TEXT_PAINT.fontMetrics.ascent
            val lineSpacing = TEXT_PAINT.fontSpacing
            for (line in lines) {
                nativeCanvas.drawText(
                    line,
                    obj.bounds.left,
                    baseline,
                    TEXT_PAINT,
                )
                baseline += lineSpacing
            }
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
                    RectF(
                        bounds.left,
                        bounds.top,
                        bounds.right,
                        bounds.bottom,
                    )
                nativeCanvas.drawBitmap(bitmap, null, rect, null)
            } else {
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
    for ((hx, hy) in cornerHandles(left, top, right, bottom)) {
        nativeCanvas.drawCircle(
            hx,
            hy,
            HANDLE_RADIUS,
            SELECTION_HANDLE_BORDER,
        )
        nativeCanvas.drawCircle(
            hx,
            hy,
            HANDLE_RADIUS - 1.5f,
            SELECTION_HANDLE_FILL,
        )
    }
}

private fun cornerHandles(
    l: Float,
    t: Float,
    r: Float,
    b: Float,
): List<Pair<Float, Float>> = listOf(l to t, r to t, l to b, r to b)

private enum class SelectionHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

private fun hitTestHandle(
    state: InkCanvasState,
    screenPos: Offset,
): SelectionHandle? {
    val bounds = state.selectionBounds ?: return null
    val sl = state.viewport.pageToScreenX(bounds.left)
    val st = state.viewport.pageToScreenY(bounds.top)
    val sr = state.viewport.pageToScreenX(bounds.right)
    val sb = state.viewport.pageToScreenY(bounds.bottom)

    fun hit(
        x: Float,
        y: Float,
    ): Boolean {
        val dx = screenPos.x - x
        val dy = screenPos.y - y
        return (dx * dx + dy * dy) <= (HANDLE_TOUCH_RADIUS_PX * HANDLE_TOUCH_RADIUS_PX)
    }

    return when {
        hit(sl, st) -> SelectionHandle.TOP_LEFT
        hit(sr, st) -> SelectionHandle.TOP_RIGHT
        hit(sl, sb) -> SelectionHandle.BOTTOM_LEFT
        hit(sr, sb) -> SelectionHandle.BOTTOM_RIGHT
        else -> null
    }
}

// ------ Gesture routing ------

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
    // Track every currently-pressed pointer position across events. Pointer events only carry
    // the pointers that changed in that event, so counting `event.changes` directly misses
    // fingers (a second finger landing arrives as a single change and would otherwise be routed
    // into the drawing branch instead of starting a pinch-zoom).
    val pointerPositions = mutableMapOf<PointerId, Offset>()
    pointerPositions[activePointerId] = down.position
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
        event.changes.forEach { change ->
            if (change.pressed) {
                pointerPositions[change.id] = change.position
            } else {
                pointerPositions.remove(change.id)
            }
        }
        when {
            pointerPositions.size >= 2 -> {
                if (strokeStarted) {
                    handler.abortStroke()
                    strokeStarted = false
                }
                val newCentroid = centroidOf(pointerPositions.values)
                val newSpan = spanOf(pointerPositions.values)
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
                event.changes.forEach { change ->
                    if (change.pressed) change.consume()
                }
            }
            pointerPositions.size == 1 -> {
                val change = event.changes.firstOrNull { it.pressed }
                if (change != null && change.id == activePointerId) {
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
                }
                change?.consume()
                pointerPositions.values.firstOrNull()?.let { centroid = it }
                span = 0f
            }
            else -> break
        }
    }
    handler.onUp()
}

private sealed interface SelectionGestureMode {
    data object Move : SelectionGestureMode

    data object Lasso : SelectionGestureMode

    data class Resize(
        val handle: SelectionHandle,
    ) : SelectionGestureMode
}

private const val TAP_SLOP: Float = 12f
private const val TAP_TIMEOUT: Long = 200L

private suspend fun AwaitPointerEventScope.handleSelectionGesture(
    state: InkCanvasState,
    down: PointerInputChange,
) {
    val activePointerId = down.id
    val downPos = down.position
    val downTime = down.uptimeMillis
    val downPage = state.viewport.screenToPage(downPos)

    val hitHandle = hitTestHandle(state, downPos)
    val initialBounds = state.selectionBounds
    val initialStrokes =
        if (hitHandle != null && initialBounds != null) {
            state.strokes.filter { it.objectId in state.selectedIds }
        } else {
            emptyList()
        }
    val initialObjects =
        if (hitHandle != null && initialBounds != null) {
            state.objects.filter { it.objectId in state.selectedIds }
        } else {
            emptyList()
        }

    val mode: SelectionGestureMode =
        if (hitHandle != null) {
            SelectionGestureMode.Resize(hitHandle)
        } else if (state.hitTestSelection(downPos.x, downPos.y)) {
            SelectionGestureMode.Move
        } else {
            val hitObject = state.selectObjectAt(downPage.x, downPage.y)
            if (hitObject != null) {
                SelectionGestureMode.Move
            } else {
                SelectionGestureMode.Lasso
            }
        }

    if (mode is SelectionGestureMode.Lasso) {
        state.addLassoPoint(downPos.x, downPos.y)
    }

    var centroid = downPos
    var span = 0f
    var lastPageX = downPage.x
    var lastPageY = downPage.y
    var totalDx = 0f
    var totalDy = 0f
    var maxDist = 0f
    var wasMultiTouch = false
    // Track all pressed pointers across events (events only carry changed pointers).
    val pointerPositions = mutableMapOf<PointerId, Offset>()
    pointerPositions[activePointerId] = downPos

    while (true) {
        val event = awaitPointerEvent()
        event.changes.forEach { change ->
            if (change.pressed) {
                pointerPositions[change.id] = change.position
            } else {
                pointerPositions.remove(change.id)
            }
        }
        when {
            pointerPositions.size >= 2 -> {
                val newCentroid = centroidOf(pointerPositions.values)
                val newSpan = spanOf(pointerPositions.values)
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
                val pageNow = state.viewport.screenToPage(newCentroid)
                lastPageX = pageNow.x
                lastPageY = pageNow.y
                event.changes.forEach { change ->
                    if (change.pressed) change.consume()
                }
            }
            pointerPositions.size == 1 -> {
                val change = event.changes.firstOrNull { it.pressed }
                if (change != null && change.id == activePointerId) {
                    val dx = change.position.x - downPos.x
                    val dy = change.position.y - downPos.y
                    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (dist > maxDist) maxDist = dist
                    when (mode) {
                        is SelectionGestureMode.Resize -> {
                            if (initialBounds != null) {
                                val pageNow = state.viewport.screenToPage(change.position)
                                val newBounds =
                                    when (mode.handle) {
                                        SelectionHandle.TOP_LEFT -> {
                                            val l = min(pageNow.x, initialBounds.right - MIN_SELECTION_SIZE)
                                            val t = min(pageNow.y, initialBounds.bottom - MIN_SELECTION_SIZE)
                                            Rect(l, t, initialBounds.right, initialBounds.bottom)
                                        }
                                        SelectionHandle.TOP_RIGHT -> {
                                            val r = max(pageNow.x, initialBounds.left + MIN_SELECTION_SIZE)
                                            val t = min(pageNow.y, initialBounds.bottom - MIN_SELECTION_SIZE)
                                            Rect(initialBounds.left, t, r, initialBounds.bottom)
                                        }
                                        SelectionHandle.BOTTOM_LEFT -> {
                                            val l = min(pageNow.x, initialBounds.right - MIN_SELECTION_SIZE)
                                            val b = max(pageNow.y, initialBounds.top + MIN_SELECTION_SIZE)
                                            Rect(l, initialBounds.top, initialBounds.right, b)
                                        }
                                        SelectionHandle.BOTTOM_RIGHT -> {
                                            val r = max(pageNow.x, initialBounds.left + MIN_SELECTION_SIZE)
                                            val b = max(pageNow.y, initialBounds.top + MIN_SELECTION_SIZE)
                                            Rect(initialBounds.left, initialBounds.top, r, b)
                                        }
                                    }
                                state.scaleSelection(
                                    initialBounds,
                                    newBounds,
                                    initialStrokes,
                                    initialObjects,
                                )
                            }
                        }
                        is SelectionGestureMode.Move -> {
                            val pageNow = state.viewport.screenToPage(change.position)
                            val mdx = pageNow.x - lastPageX
                            val mdy = pageNow.y - lastPageY
                            state.moveSelection(mdx, mdy)
                            totalDx += mdx
                            totalDy += mdy
                            lastPageX = pageNow.x
                            lastPageY = pageNow.y
                        }
                        is SelectionGestureMode.Lasso -> {
                            state.addLassoPoint(change.position.x, change.position.y)
                        }
                    }
                }
                change?.consume()
                pointerPositions.values.firstOrNull()?.let { centroid = it }
                span = 0f
            }
            else -> break
        }
    }
    if (wasMultiTouch) {
        state.lassoPoints = emptyList()
        return
    }
    val elapsed = System.currentTimeMillis() - downTime
    val isTap = maxDist < TAP_SLOP && elapsed < TAP_TIMEOUT
    when (mode) {
        is SelectionGestureMode.Resize -> {
            if (!isTap && initialBounds != null) {
                state.commitResize(initialStrokes, initialObjects)
            }
        }
        is SelectionGestureMode.Move -> {
            if (!isTap) {
                state.commitMove(totalDx, totalDy)
            }
        }
        is SelectionGestureMode.Lasso -> {
            if (isTap) {
                state.lassoPoints = emptyList()
                state.clearSelectionPublic()
            } else {
                state.selectLasso(state.lassoPoints)
            }
        }
    }
}
