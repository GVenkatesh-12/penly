package com.penly.editor.canvas

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import com.penly.core.common.PenlyIds
import com.penly.core.geometry.Point
import com.penly.core.geometry.Rect
import com.penly.core.geometry.Transform
import com.penly.core.ink.BrushFactory
import com.penly.core.ink.CanvasViewport
import com.penly.core.ink.PenTool
import com.penly.core.ink.StrokeRecord
import com.penly.core.model.ImageObject
import com.penly.core.model.ObjectId
import com.penly.core.model.PageObject
import com.penly.core.model.TextObject
import com.penly.editor.history.UndoRedoStack
import com.penly.editor.selection.LassoPath
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** In-memory clipboard payload holding copied strokes, non-ink objects and decoded bitmaps. */
data class ClipboardData(
    val strokes: List<StrokeRecord>,
    val objects: List<PageObject>,
    val images: Map<ObjectId, Bitmap>,
    val originBounds: Rect,
)

/**
 * Mutable editor state for the ink canvas: committed strokes, non-ink page objects (text,
 * image), live lasso selection, and an undo/redo command stack. Every page mutation goes
 * through an [EditorCommand] pushed onto the stack so undo/redo covers strokes, objects, moves
 * and deletes uniformly.
 */
class InkCanvasState {
    val renderer: CanvasStrokeRenderer = CanvasStrokeRenderer.create()

    var viewport by mutableStateOf(CanvasViewport.INITIAL)
        private set

    var tool by mutableStateOf(PenTool.PEN)
        private set

    val strokes = mutableStateListOf<StrokeRecord>()

    /** Non-ink page objects (Text/Image/Opaque), in paint order. */
    var objects by mutableStateOf(listOf<PageObject>())
        private set

    var inProgressStroke by mutableStateOf<InProgressStroke?>(null)
        private set

    private var selectionModeState by mutableStateOf(false)

    /** When true, canvas gestures select/move instead of draw/erase. */
    val selectionMode: Boolean
        get() = selectionModeState

    var selectedIds by mutableStateOf(setOf<ObjectId>())
        private set

    /** Page-space union of selected effective bounds; null when nothing is selected. */
    var selectionBounds by mutableStateOf<Rect?>(null)
        private set

    /** Page-space lasso polyline while a lasso drag is in progress; cleared on release. */
    var lassoPoints by mutableStateOf(listOf<Point>())
        internal set

    /** Last measured canvas size in pixels; used to compute the viewport-center insert point. */
    var lastCanvasSize by mutableStateOf(Size.Zero)
        private set

    /** Image bitmaps for [ImageObject]s, injected after load (the payloadRef names the asset). */
    val images = mutableStateMapOf<ObjectId, Bitmap>()

    /** In-memory clipboard for copied/cut page items. */
    var clipboard by mutableStateOf<ClipboardData?>(null)
        private set

    val canPaste: Boolean
        get() = clipboard != null

    /** Invoked after any page mutation, with a snapshot of all strokes. */
    var onStrokesChanged: ((List<StrokeRecord>) -> Unit)? = null

    private var commands = UndoRedoStack<EditorCommand>()
    private var canUndoState by mutableStateOf(false)
    private var canRedoState by mutableStateOf(false)
    private val drawTick = mutableIntStateOf(0)

    val canUndo: Boolean
        get() = canUndoState

    val canRedo: Boolean
        get() = canRedoState

    val currentTick: Int
        get() = drawTick.intValue

    fun selectTool(tool: PenTool) {
        this.tool = tool
    }

    /** Enables/disables selection mode; disabling clears the current selection and lasso. */
    fun setSelectionMode(enabled: Boolean) {
        if (selectionModeState == enabled) return
        selectionModeState = enabled
        if (!enabled) {
            clearSelection()
        }
        lassoPoints = emptyList()
        bumpTick()
    }

    fun isSelected(id: ObjectId): Boolean = id in selectedIds

    fun setCanvasSize(size: Size) {
        lastCanvasSize = size
    }

    fun setImage(
        objectId: ObjectId,
        bitmap: Bitmap,
    ) {
        images[objectId] = bitmap
    }

    /** Appends a page-space lasso point (converted from screen). */
    fun addLassoPoint(
        screenX: Float,
        screenY: Float,
    ) {
        val pt = Point(viewport.screenToPageX(screenX), viewport.screenToPageY(screenY))
        lassoPoints = lassoPoints + pt
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
        try {
            stroke.start(BrushFactory.createBrush(tool, tool.defaultSize, tool.defaultColorArgb))
            stroke.enqueueInputs(MutableStrokeInputBatch().add(firstInput), emptyBatch())
            stroke.updateShape()
        } catch (exception: RuntimeException) {
            Log.e(TAG, "startStroke rejected input", exception)
            return
        }
        inProgressStroke = stroke
        bounds.set(firstInput.x, firstInput.y, firstInput.x, firstInput.y)
        bumpTick()
    }

    fun addInput(
        input: StrokeInput,
        bounds: RectF,
    ) {
        val stroke = inProgressStroke ?: return
        try {
            stroke.enqueueInputs(MutableStrokeInputBatch().add(input), emptyBatch())
            stroke.updateShape()
        } catch (exception: RuntimeException) {
            Log.e(TAG, "addInput rejected input", exception)
            return
        }
        bounds.union(input.x, input.y)
        bumpTick()
    }

    fun endStroke(bounds: RectF) {
        val stroke = inProgressStroke ?: return
        inProgressStroke = null
        try {
            stroke.finishInput()
            stroke.updateShape()
            val immutable = stroke.toImmutable()
            val computedBounds = computeStrokeBounds(immutable, bounds)
            val record =
                StrokeRecord(
                    objectId = ObjectId(PenlyIds.newId()),
                    stroke = immutable,
                    bounds = computedBounds,
                )
            val command = AddStroke(record)
            pushCommand(command)
            command.redo(this)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "endStroke rejected stroke", exception)
            bumpTick()
        }
    }

    fun loadRecords(records: List<StrokeRecord>) {
        strokes.clear()
        commands = UndoRedoStack()
        syncHistoryState()
        strokes.addAll(records)
        bumpTick()
    }

    /** Replaces the non-ink objects; also clears the injected image bitmaps. */
    fun loadObjects(objects: List<PageObject>) {
        this.objects = objects
        images.clear()
        clearSelection()
        bumpTick()
    }

    fun abortStroke() {
        inProgressStroke = null
        bumpTick()
    }

    fun undo() {
        val command = commands.undo() ?: return
        syncHistoryState()
        command.undo(this)
        reconcileSelection()
    }

    fun redo() {
        val command = commands.redo() ?: return
        syncHistoryState()
        command.redo(this)
        reconcileSelection()
    }

    fun clearAll() {
        strokes.clear()
        objects = emptyList()
        images.clear()
        commands = UndoRedoStack()
        syncHistoryState()
        inProgressStroke = null
        clearSelection()
        onStrokesChanged?.invoke(strokes.toList())
        bumpTick()
    }

    fun findHitStroke(
        pageX: Float,
        pageY: Float,
        radius: Float,
    ): StrokeRecord? {
        val touchPt = Point(pageX, pageY)
        for (index in strokes.indices.reversed()) {
            val record = strokes[index]
            val b = record.bounds
            if (
                pageX < b.left - radius ||
                pageX > b.right + radius ||
                pageY < b.top - radius ||
                pageY > b.bottom + radius
            ) {
                continue
            }
            var hit = false
            var prev: Point? = null
            val inputs = record.stroke.inputs
            for (i in 0 until inputs.size) {
                val input = inputs.get(i)
                val pt = record.transform.apply(Point(input.x, input.y))
                if (distance(touchPt, pt) <= radius) {
                    hit = true
                    break
                }
                if (prev != null && distanceToSegment(touchPt, prev, pt) <= radius) {
                    hit = true
                    break
                }
                prev = pt
            }
            if (hit) return record
        }
        return null
    }

    fun findHitObject(
        pageX: Float,
        pageY: Float,
        radius: Float,
    ): PageObject? =
        objects.lastOrNull { obj ->
            val b = obj.bounds
            val expanded =
                Rect(
                    b.left - radius,
                    b.top - radius,
                    b.right + radius,
                    b.bottom + radius,
                )
            expanded.contains(pageX, pageY)
        }

    /**
     * Erases a hit item immediately from the active canvas collections without pushing
     * a command yet, allowing continuous gestures to accumulate all deletions into a single
     * undo step.
     */
    fun eraseImmediately(
        pageX: Float,
        pageY: Float,
        radius: Float,
    ): Pair<Pair<Int, StrokeRecord>?, Pair<Int, PageObject>?> {
        val hitStroke = findHitStroke(pageX, pageY, radius)
        if (hitStroke != null) {
            val index = strokes.indexOf(hitStroke)
            strokes.removeAt(index)
            notifyPageChanged()
            return (index to hitStroke) to null
        }
        val hitObject = findHitObject(pageX, pageY, radius)
        if (hitObject != null) {
            val index = objects.indexOf(hitObject)
            val list = objects.toMutableList()
            list.removeAt(index)
            objects = list
            notifyPageChanged()
            return null to (index to hitObject)
        }
        return null to null
    }

    /** Commits an erase gesture's accumulated deletions as a single atomic undoable command. */
    fun commitEraseGesture(
        removedStrokes: List<Pair<Int, StrokeRecord>>,
        removedObjects: List<Pair<Int, PageObject>>,
    ) {
        if (removedStrokes.isEmpty() && removedObjects.isEmpty()) return
        val command =
            DeleteSelection(
                removedStrokes = removedStrokes.map { it.second },
                strokeIndices = removedStrokes.map { it.first },
                removedObjects = removedObjects.map { it.second },
                objectIndices = removedObjects.map { it.first },
            )
        pushCommand(command)
        notifyPageChanged()
    }

    /** Restores items erased in a gesture that was aborted. */
    fun abortEraseGesture(
        removedStrokes: List<Pair<Int, StrokeRecord>>,
        removedObjects: List<Pair<Int, PageObject>>,
    ) {
        if (removedStrokes.isNotEmpty()) {
            restoreStrokesInternal(
                removedStrokes.map { it.first },
                removedStrokes.map { it.second },
            )
        }
        if (removedObjects.isNotEmpty()) {
            restoreObjectsInternal(
                removedObjects.map { it.first },
                removedObjects.map { it.second },
            )
        }
    }

    fun eraseAt(
        pageX: Float,
        pageY: Float,
        radius: Float,
    ) {
        val hitStroke = findHitStroke(pageX, pageY, radius)
        if (hitStroke != null) {
            val command =
                DeleteSelection(
                    removedStrokes = listOf(hitStroke),
                    strokeIndices = listOf(strokes.indexOf(hitStroke)),
                    removedObjects = emptyList(),
                    objectIndices = emptyList(),
                )
            pushCommand(command)
            command.redo(this)
            return
        }
        val hitObject = findHitObject(pageX, pageY, radius)
        if (hitObject != null) {
            val command =
                DeleteSelection(
                    removedStrokes = emptyList(),
                    strokeIndices = emptyList(),
                    removedObjects = listOf(hitObject),
                    objectIndices = listOf(objects.indexOf(hitObject)),
                )
            pushCommand(command)
            command.redo(this)
        }
    }

    /** Inserts a text object at page position [at]; the action is undoable. */
    fun insertText(
        text: String,
        fontSize: Float,
        colorArgb: Int,
        at: Point,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val paint =
            android.graphics.Paint().apply {
                textSize = fontSize
            }
        val lines = trimmed.split('\n')
        val maxWidth = lines.maxOfOrNull { paint.measureText(it) }?.coerceAtLeast(20f) ?: 20f
        val fm = paint.fontMetrics
        val singleLineHeight = fm.descent - fm.ascent
        val totalHeight =
            if (lines.size <= 1) {
                singleLineHeight
            } else {
                singleLineHeight + (lines.size - 1) * paint.fontSpacing
            }
        val now = System.currentTimeMillis()
        val objectText =
            TextObject(
                objectId = ObjectId(PenlyIds.newId()),
                bounds = Rect(at.x, at.y, at.x + maxWidth, at.y + totalHeight),
                createdAtMillis = now,
                updatedAtMillis = now,
                text = trimmed,
                fontSize = fontSize,
                colorArgb = colorArgb,
            )
        val command = InsertObjects(listOf(objectText))
        pushCommand(command)
        command.redo(this)
    }

    /** Inserts an image object; the bitmap is delivered separately via [setImage]. */
    fun insertImage(
        objectId: ObjectId,
        bounds: Rect,
        mimeType: String,
        payloadRef: String?,
    ) {
        val now = System.currentTimeMillis()
        val image =
            ImageObject(
                objectId = objectId,
                bounds = bounds,
                createdAtMillis = now,
                updatedAtMillis = now,
                payloadRef = payloadRef,
                mimeType = mimeType,
            )
        val command = InsertObjects(listOf(image))
        pushCommand(command)
        command.redo(this)
    }

    /** Deletes every selected object through an undoable [DeleteSelection] command. */
    fun deleteSelection() {
        if (selectedIds.isEmpty()) return
        val ids = selectedIds
        val removedStrokes = strokes.filter { it.objectId in ids }
        val removedObjects = objects.filter { it.objectId in ids }
        val command =
            DeleteSelection(
                removedStrokes = removedStrokes,
                strokeIndices = removedStrokes.map { strokes.indexOf(it) },
                removedObjects = removedObjects,
                objectIndices = removedObjects.map { objects.indexOf(it) },
            )
        pushCommand(command)
        command.redo(this)
        clearSelection()
    }

    /** Copies the currently selected strokes and objects to the in-memory clipboard. */
    fun copySelection() {
        if (selectedIds.isEmpty()) return
        val ids = selectedIds
        val selStrokes = strokes.filter { it.objectId in ids }
        val selObjects = objects.filter { it.objectId in ids }
        val selImages =
            selObjects
                .filterIsInstance<ImageObject>()
                .mapNotNull { obj ->
                    images[obj.objectId]?.let { obj.objectId to it }
                }.toMap()
        val bounds = selectionBounds ?: Rect(0f, 0f, 0f, 0f)
        clipboard =
            ClipboardData(
                strokes = selStrokes,
                objects = selObjects,
                images = selImages,
                originBounds = bounds,
            )
    }

    /** Cuts the currently selected objects (copies to clipboard, then deletes from canvas). */
    fun cutSelection() {
        if (selectedIds.isEmpty()) return
        copySelection()
        deleteSelection()
    }

    /** Pastes clipboard content at [targetCenter] or viewport center, and selects the pasted items. */
    fun paste(targetCenter: Point? = null): Boolean {
        val clip = clipboard ?: return false
        val center =
            targetCenter ?: run {
                val size = lastCanvasSize
                Point(
                    viewport.screenToPageX(size.width / 2f),
                    viewport.screenToPageY(size.height / 2f),
                )
            }
        val origCenter = clip.originBounds.center
        val dx = center.x - origCenter.x
        val dy = center.y - origCenter.y

        val newStrokes =
            clip.strokes.map { record ->
                val newId = ObjectId(PenlyIds.newId())
                translateRecord(record, dx, dy).copy(objectId = newId)
            }
        val newObjects =
            clip.objects.map { obj ->
                val newId = ObjectId(PenlyIds.newId())
                if (obj is ImageObject) {
                    clip.images[obj.objectId]?.let { images[newId] = it }
                }
                val translated =
                    obj.withTransform(
                        transform = obj.transform.translate(dx, dy),
                        bounds = obj.bounds.translate(dx, dy),
                    )
                translated.withObjectId(newId)
            }
        val command = InsertItems(newStrokes, newObjects)
        pushCommand(command)
        command.redo(this)

        selectedIds = (newStrokes.map { it.objectId } + newObjects.map { it.objectId }).toSet()
        selectionBounds = computeSelectionBounds(selectedIds)
        setSelectionMode(true)
        bumpTick()
        return true
    }

    /** Duplicates currently selected items with a small offset and selects the duplicates. */
    fun duplicateSelection(offset: Point = Point(DUPLICATE_OFFSET, DUPLICATE_OFFSET)) {
        if (selectedIds.isEmpty()) return
        val ids = selectedIds
        val clonedStrokes =
            strokes
                .filter { it.objectId in ids }
                .map { record ->
                    val newId = ObjectId(PenlyIds.newId())
                    translateRecord(record, offset.x, offset.y).copy(objectId = newId)
                }
        val clonedObjects =
            objects
                .filter { it.objectId in ids }
                .map { obj ->
                    val newId = ObjectId(PenlyIds.newId())
                    if (obj is ImageObject) {
                        images[obj.objectId]?.let { images[newId] = it }
                    }
                    val translated =
                        obj.withTransform(
                            transform = obj.transform.translate(offset.x, offset.y),
                            bounds = obj.bounds.translate(offset.x, offset.y),
                        )
                    translated.withObjectId(newId)
                }
        if (clonedStrokes.isNotEmpty() || clonedObjects.isNotEmpty()) {
            val command = InsertItems(clonedStrokes, clonedObjects)
            pushCommand(command)
            command.redo(this)

            selectedIds = (clonedStrokes.map { it.objectId } + clonedObjects.map { it.objectId }).toSet()
            selectionBounds = computeSelectionBounds(selectedIds)
            bumpTick()
        }
    }

    /**
     * Live-moves every selected object by ([dx], [dy]) page units. Applies the transform to
     * both the record/object transform and its effective bounds so hit-testing stays correct.
     * Does not push a command — [commitMove] records it once on gesture up.
     */
    fun moveSelection(
        dx: Float,
        dy: Float,
    ) {
        if (selectedIds.isEmpty()) return
        val ids = selectedIds
        for (index in strokes.indices) {
            val record = strokes[index]
            if (record.objectId in ids) {
                strokes[index] = translateRecord(record, dx, dy)
            }
        }
        objects =
            objects.map { obj ->
                if (obj.objectId in ids) {
                    obj.withTransform(
                        transform = obj.transform.translate(dx, dy),
                        bounds = obj.bounds.translate(dx, dy),
                    )
                } else {
                    obj
                }
            }
        selectionBounds = selectionBounds?.translate(dx, dy)
        bumpTick()
    }

    /** Records the move gesture's total delta as an undoable command. */
    fun commitMove(
        dx: Float,
        dy: Float,
    ) {
        if (selectedIds.isEmpty() || (dx == 0f && dy == 0f)) return
        pushCommand(MoveSelection(selectedIds.toList(), dx, dy))
        onStrokesChanged?.invoke(strokes.toList())
        bumpTick()
    }

    /**
     * Resizes selected items proportionally from [initialBounds] to [newBounds].
     */
    fun scaleSelection(
        initialBounds: Rect,
        newBounds: Rect,
        initialStrokes: List<StrokeRecord>,
        initialObjects: List<PageObject>,
    ) {
        if (initialBounds.width <= 0f || initialBounds.height <= 0f) return
        val scaleX = newBounds.width / initialBounds.width
        val scaleY = newBounds.height / initialBounds.height
        val ids = selectedIds

        for (index in strokes.indices) {
            val record = strokes[index]
            if (record.objectId in ids) {
                val initial =
                    initialStrokes.firstOrNull { it.objectId == record.objectId } ?: record
                val relLeft = (initial.bounds.left - initialBounds.left) / initialBounds.width
                val relTop = (initial.bounds.top - initialBounds.top) / initialBounds.height
                val relRight = (initial.bounds.right - initialBounds.left) / initialBounds.width
                val relBottom = (initial.bounds.bottom - initialBounds.top) / initialBounds.height

                val newLeft = newBounds.left + relLeft * newBounds.width
                val newTop = newBounds.top + relTop * newBounds.height
                val newRight = newBounds.left + relRight * newBounds.width
                val newBottom = newBounds.top + relBottom * newBounds.height

                val newTransform =
                    Transform(
                        translationX =
                            newBounds.left +
                                (initial.transform.translationX - initialBounds.left) * scaleX,
                        translationY =
                            newBounds.top +
                                (initial.transform.translationY - initialBounds.top) * scaleY,
                        scaleX = initial.transform.scaleX * scaleX,
                        scaleY = initial.transform.scaleY * scaleY,
                        rotationDegrees = initial.transform.rotationDegrees,
                    )
                strokes[index] =
                    record.copy(
                        transform = newTransform,
                        bounds = RectF(newLeft, newTop, newRight, newBottom),
                    )
            }
        }

        objects =
            objects.map { obj ->
                if (obj.objectId in ids) {
                    val initial =
                        initialObjects.firstOrNull { it.objectId == obj.objectId } ?: obj
                    val relLeft = (initial.bounds.left - initialBounds.left) / initialBounds.width
                    val relTop = (initial.bounds.top - initialBounds.top) / initialBounds.height
                    val relRight = (initial.bounds.right - initialBounds.left) / initialBounds.width
                    val relBottom = (initial.bounds.bottom - initialBounds.top) / initialBounds.height

                    val newObjBounds =
                        Rect(
                            newBounds.left + relLeft * newBounds.width,
                            newBounds.top + relTop * newBounds.height,
                            newBounds.left + relRight * newBounds.width,
                            newBounds.top + relBottom * newBounds.height,
                        )
                    if (obj is TextObject && initial is TextObject) {
                        val avgScale = (scaleX + scaleY) / 2f
                        obj.copy(
                            fontSize = (initial.fontSize * avgScale).coerceAtLeast(8f),
                            bounds = newObjBounds,
                        )
                    } else {
                        obj.withTransform(
                            transform = obj.transform,
                            bounds = newObjBounds,
                        )
                    }
                } else {
                    obj
                }
            }
        selectionBounds = newBounds
        bumpTick()
    }

    /** Commits the resize gesture as an undoable command. */
    fun commitResize(
        initialStrokes: List<StrokeRecord>,
        initialObjects: List<PageObject>,
    ) {
        val currentStrokes = strokes.filter { it.objectId in selectedIds }
        val currentObjects = objects.filter { it.objectId in selectedIds }
        if (currentStrokes == initialStrokes && currentObjects == initialObjects) return
        val command =
            ResizeSelection(
                oldStrokes = initialStrokes,
                newStrokes = currentStrokes,
                oldObjects = initialObjects,
                newObjects = currentObjects,
            )
        pushCommand(command)
        notifyPageChanged()
    }

    /**
     * Selects everything intersecting the lasso polygon: strokes
     * whose effective bounds intersect the polygon or have any
     * input point inside it, plus non-ink objects whose bounds
     * intersect. A degenerate polygon clears the selection.
     */
    fun selectLasso(pagePoints: List<Point>) {
        lassoPoints = emptyList()
        val path = LassoPath.of(pagePoints)
        if (path.bounds.isEmpty) {
            clearSelection()
            return
        }
        val ids = mutableSetOf<ObjectId>()
        val pathBounds = path.bounds
        for (record in strokes) {
            val bounds = record.bounds
            if (bounds.right < pathBounds.left ||
                bounds.left > pathBounds.right ||
                bounds.bottom < pathBounds.top ||
                bounds.top > pathBounds.bottom
            ) {
                continue
            }
            var hit = false
            var prevPoint: Point? = null
            for (index in 0 until record.stroke.inputs.size) {
                val input = record.stroke.inputs.get(index)
                val pt = record.transform.apply(Point(input.x, input.y))
                if (path.contains(pt)) {
                    hit = true
                    break
                }
                if (prevPoint != null && path.intersectsSegment(prevPoint, pt)) {
                    hit = true
                    break
                }
                prevPoint = pt
            }
            if (hit) ids += record.objectId
        }
        for (obj in objects) {
            if (path.intersects(obj.bounds)) ids += obj.objectId
        }
        selectedIds = ids
        selectionBounds = computeSelectionBounds(ids)
        bumpTick()
    }

    /**
     * Direct selection: selects the object (image/text/stroke) at the given page position.
     * Tapping an item adds it to the current multi-selection (standard lasso-tool behavior),
     * so users can tap several items in a row and then move or delete them together.
     * Returns the object id if hit, null if nothing was hit.
     */
    fun selectObjectAt(
        pageX: Float,
        pageY: Float,
    ): ObjectId? {
        val touchSlop = (MIN_TOUCH_TARGET_PX / 2f) / viewport.scale
        // 1. Check topmost PageObject (Text, Image) with touch padding
        val hitObj =
            objects.lastOrNull { obj ->
                val b = obj.bounds
                val expanded =
                    Rect(
                        b.left - touchSlop,
                        b.top - touchSlop,
                        b.right + touchSlop,
                        b.bottom + touchSlop,
                    )
                expanded.contains(pageX, pageY)
            }
        if (hitObj != null) {
            selectedIds = selectedIds + hitObj.objectId
            selectionBounds = computeSelectionBounds(selectedIds)
            bumpTick()
            return hitObj.objectId
        }

        // 2. Check strokes in reverse paint order
        val tapPt = Point(pageX, pageY)
        for (index in strokes.indices.reversed()) {
            val record = strokes[index]
            val b = record.bounds
            if (
                pageX < b.left - touchSlop ||
                pageX > b.right + touchSlop ||
                pageY < b.top - touchSlop ||
                pageY > b.bottom + touchSlop
            ) {
                continue
            }
            var hit = false
            var prev: Point? = null
            for (i in 0 until record.stroke.inputs.size) {
                val input = record.stroke.inputs.get(i)
                val pt = record.transform.apply(Point(input.x, input.y))
                if (distance(tapPt, pt) <= touchSlop) {
                    hit = true
                    break
                }
                if (prev != null && distanceToSegment(tapPt, prev, pt) <= touchSlop) {
                    hit = true
                    break
                }
                prev = pt
            }
            if (hit) {
                selectedIds = selectedIds + record.objectId
                selectionBounds = computeSelectionBounds(selectedIds)
                bumpTick()
                return record.objectId
            }
        }
        return null
    }

    /** True when the screen point lies inside the current selection bounds (page space). */
    fun hitTestSelection(
        screenX: Float,
        screenY: Float,
    ): Boolean {
        val bounds = selectionBounds ?: return false
        val pageX = viewport.screenToPageX(screenX)
        val pageY = viewport.screenToPageY(screenY)
        val minHalfSize = (MIN_TOUCH_TARGET_PX / 2f) / viewport.scale
        val hitLeft = min(bounds.left, bounds.center.x - minHalfSize)
        val hitTop = min(bounds.top, bounds.center.y - minHalfSize)
        val hitRight = max(bounds.right, bounds.center.x + minHalfSize)
        val hitBottom = max(bounds.bottom, bounds.center.y + minHalfSize)
        val hitRect = Rect(hitLeft, hitTop, hitRight, hitBottom)
        return hitRect.contains(pageX, pageY)
    }

    private fun computeSelectionBounds(ids: Set<ObjectId>): Rect? {
        if (ids.isEmpty()) return null
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var hasItem = false
        for (record in strokes) {
            if (record.objectId !in ids) continue
            val bounds = record.bounds
            minX = min(minX, bounds.left)
            minY = min(minY, bounds.top)
            maxX = max(maxX, bounds.right)
            maxY = max(maxY, bounds.bottom)
            hasItem = true
        }
        for (obj in objects) {
            if (obj.objectId !in ids) continue
            val bounds = obj.bounds
            minX = min(minX, bounds.left)
            minY = min(minY, bounds.top)
            maxX = max(maxX, bounds.right)
            maxY = max(maxY, bounds.bottom)
            hasItem = true
        }
        if (!hasItem) return null
        // Pad the selection bounds so stroke rendering overshoot
        // doesn't visually overflow the selection rect.
        return Rect(minX, minY, maxX, maxY).inset(-SELECTION_PADDING)
    }

    private fun clearSelection() {
        selectedIds = emptySet()
        selectionBounds = null
    }

    /** Public entry point to clear the selection (e.g. tap-to-deselect). */
    fun clearSelectionPublic() {
        clearSelection()
        bumpTick()
    }

    private fun addStrokeInternal(record: StrokeRecord) {
        strokes.add(record)
        notifyPageChanged()
    }

    private fun addStrokesInternal(records: List<StrokeRecord>) {
        strokes.addAll(records)
        notifyPageChanged()
    }

    private fun removeStrokeInternal(objectId: ObjectId) {
        strokes.removeAll { it.objectId == objectId }
        notifyPageChanged()
    }

    private fun removeStrokesInternal(objectIds: List<ObjectId>) {
        if (objectIds.isEmpty()) return
        val ids = objectIds.toSet()
        strokes.removeAll { it.objectId in ids }
        notifyPageChanged()
    }

    private fun restoreStrokesInternal(
        indices: List<Int>,
        records: List<StrokeRecord>,
    ) {
        for ((index, record) in indices.zip(records)) {
            strokes.add(index.coerceIn(0, strokes.size), record)
        }
        notifyPageChanged()
    }

    private fun addObjectsInternal(objects: List<PageObject>) {
        this.objects = this.objects + objects
        notifyPageChanged()
    }

    private fun removeObjectsInternal(objectIds: List<ObjectId>) {
        if (objectIds.isEmpty()) return
        val ids = objectIds.toSet()
        this.objects = this.objects.filterNot { it.objectId in ids }
        notifyPageChanged()
    }

    private fun restoreObjectsInternal(
        indices: List<Int>,
        objects: List<PageObject>,
    ) {
        val list = this.objects.toMutableList()
        for ((index, obj) in indices.zip(objects)) {
            list.add(index.coerceIn(0, list.size), obj)
        }
        this.objects = list
        notifyPageChanged()
    }

    private fun translateObjectsInternal(
        objectIds: List<ObjectId>,
        dx: Float,
        dy: Float,
    ) {
        if (objectIds.isEmpty()) return
        val ids = objectIds.toSet()
        for (index in strokes.indices) {
            val record = strokes[index]
            if (record.objectId in ids) {
                strokes[index] = translateRecord(record, dx, dy)
            }
        }
        objects =
            objects.map { obj ->
                if (obj.objectId in ids) {
                    obj.withTransform(
                        transform = obj.transform.translate(dx, dy),
                        bounds = obj.bounds.translate(dx, dy),
                    )
                } else {
                    obj
                }
            }
        selectionBounds = computeSelectionBounds(selectedIds)
        notifyPageChanged()
    }

    private fun translateRecord(
        record: StrokeRecord,
        dx: Float,
        dy: Float,
    ): StrokeRecord {
        val bounds = record.bounds
        return record.copy(
            transform = record.transform.translate(dx, dy),
            bounds =
                RectF(
                    bounds.left + dx,
                    bounds.top + dy,
                    bounds.right + dx,
                    bounds.bottom + dy,
                ),
        )
    }

    private fun replaceItemsInternal(
        newStrokes: List<StrokeRecord>,
        newObjects: List<PageObject>,
    ) {
        val strokeMap = newStrokes.associateBy { it.objectId }
        for (index in strokes.indices) {
            val updated = strokeMap[strokes[index].objectId]
            if (updated != null) {
                strokes[index] = updated
            }
        }
        val objectMap = newObjects.associateBy { it.objectId }
        objects =
            objects.map { obj ->
                objectMap[obj.objectId] ?: obj
            }
        selectionBounds = computeSelectionBounds(selectedIds)
        notifyPageChanged()
    }

    private fun notifyPageChanged() {
        onStrokesChanged?.invoke(strokes.toList())
        bumpTick()
    }

    private fun pushCommand(command: EditorCommand) {
        commands.push(command)
        syncHistoryState()
    }

    /** Mirrors the command stack into observable state so Undo/Redo buttons update in place. */
    private fun syncHistoryState() {
        canUndoState = commands.canUndo
        canRedoState = commands.canRedo
    }

    /**
     * Drops selection entries that no longer exist on the page (e.g. after undoing the stroke
     * that was selected) and recomputes the selection bounds so the selection box never floats
     * around deleted content.
     */
    private fun reconcileSelection() {
        if (selectedIds.isEmpty()) return
        val validIds =
            selectedIds.filter { id ->
                strokes.any { it.objectId == id } || objects.any { it.objectId == id }
            }
        if (validIds.isEmpty()) {
            clearSelection()
        } else {
            selectedIds = validIds.toSet()
            selectionBounds = computeSelectionBounds(selectedIds)
        }
        bumpTick()
    }

    private fun bumpTick() {
        drawTick.intValue++
    }

    /**
     * An undoable page mutation. [undo] reverts it, [redo] re-applies it; the canvas applies
     * the redo effect immediately when a command is pushed, so the stack entry is the undo
     * record of the state it just produced.
     */
    private sealed interface EditorCommand {
        fun undo(state: InkCanvasState)

        fun redo(state: InkCanvasState)
    }

    private class AddStroke(
        val record: StrokeRecord,
    ) : EditorCommand {
        override fun undo(state: InkCanvasState) = state.removeStrokeInternal(record.objectId)

        override fun redo(state: InkCanvasState) = state.addStrokeInternal(record)
    }

    private class MoveSelection(
        val objectIds: List<ObjectId>,
        val dx: Float,
        val dy: Float,
    ) : EditorCommand {
        override fun undo(state: InkCanvasState) = state.translateObjectsInternal(objectIds, -dx, -dy)

        override fun redo(state: InkCanvasState) = state.translateObjectsInternal(objectIds, dx, dy)
    }

    private class ResizeSelection(
        val oldStrokes: List<StrokeRecord>,
        val newStrokes: List<StrokeRecord>,
        val oldObjects: List<PageObject>,
        val newObjects: List<PageObject>,
    ) : EditorCommand {
        override fun undo(state: InkCanvasState) = state.replaceItemsInternal(oldStrokes, oldObjects)

        override fun redo(state: InkCanvasState) = state.replaceItemsInternal(newStrokes, newObjects)
    }

    private class DeleteSelection(
        val removedStrokes: List<StrokeRecord>,
        val strokeIndices: List<Int>,
        val removedObjects: List<PageObject>,
        val objectIndices: List<Int>,
    ) : EditorCommand {
        override fun undo(state: InkCanvasState) {
            state.restoreStrokesInternal(strokeIndices, removedStrokes)
            state.restoreObjectsInternal(objectIndices, removedObjects)
        }

        override fun redo(state: InkCanvasState) {
            state.removeStrokesInternal(removedStrokes.map { it.objectId })
            state.removeObjectsInternal(removedObjects.map { it.objectId })
        }
    }

    private class InsertItems(
        val records: List<StrokeRecord>,
        val inserted: List<PageObject>,
    ) : EditorCommand {
        override fun undo(state: InkCanvasState) {
            state.removeStrokesInternal(records.map { it.objectId })
            state.removeObjectsInternal(inserted.map { it.objectId })
        }

        override fun redo(state: InkCanvasState) {
            state.addStrokesInternal(records)
            state.addObjectsInternal(inserted)
        }
    }

    private class InsertObjects(
        val inserted: List<PageObject>,
    ) : EditorCommand {
        override fun undo(state: InkCanvasState) = state.removeObjectsInternal(inserted.map { it.objectId })

        override fun redo(state: InkCanvasState) = state.addObjectsInternal(inserted)
    }

    private companion object {
        const val TAG: String = "InkCanvasState"
        const val SELECTION_PADDING: Float = 4f
        const val MIN_TOUCH_TARGET_PX: Float = 48f
        const val DUPLICATE_OFFSET: Float = 24f

        /** Fresh empty batch per use — avoids shared mutable state. */
        fun emptyBatch() = MutableStrokeInputBatch()
    }
}

private fun computeStrokeBounds(
    stroke: androidx.ink.strokes.Stroke,
    fallback: RectF,
): RectF {
    if (stroke.inputs.size == 0) return RectF(fallback)
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (i in 0 until stroke.inputs.size) {
        val input = stroke.inputs.get(i)
        if (input.x < minX) minX = input.x
        if (input.x > maxX) maxX = input.x
        if (input.y < minY) minY = input.y
        if (input.y > maxY) maxY = input.y
    }
    return RectF(minX, minY, maxX, maxY)
}

private fun distance(
    p1: Point,
    p2: Point,
): Float = hypot((p1.x - p2.x).toDouble(), (p1.y - p2.y).toDouble()).toFloat()

private fun distanceToSegment(
    p: Point,
    a: Point,
    b: Point,
): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSq = dx * dx + dy * dy
    if (lengthSq == 0f) return distance(p, a)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSq).coerceIn(0f, 1f)
    val projX = a.x + t * dx
    val projY = a.y + t * dy
    return distance(p, Point(projX, projY))
}
