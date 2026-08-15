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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import com.penly.core.common.PenlyIds
import com.penly.core.geometry.Point
import com.penly.core.geometry.Rect
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

    /** Screen-space lasso polyline while a lasso drag is in progress; cleared on release. */
    var lassoPoints by mutableStateOf(listOf<Offset>())
        private set

    /** Last measured canvas size in pixels; used to compute the viewport-center insert point. */
    var lastCanvasSize by mutableStateOf(Size.Zero)
        private set

    /** Image bitmaps for [ImageObject]s, injected after load (the payloadRef names the asset). */
    val images = mutableStateMapOf<ObjectId, Bitmap>()

    /** Invoked after any page mutation, with a snapshot of all strokes. */
    var onStrokesChanged: ((List<StrokeRecord>) -> Unit)? = null

    private var commands = UndoRedoStack<EditorCommand>()
    private val drawTick = mutableIntStateOf(0)

    val canUndo: Boolean
        get() = commands.canUndo

    val canRedo: Boolean
        get() = commands.canRedo

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

    /** Appends a screen-space lasso point while a lasso drag is in progress. */
    fun addLassoPoint(position: Offset) {
        lassoPoints = lassoPoints + position
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
            stroke.enqueueInputs(MutableStrokeInputBatch().add(firstInput), EMPTY_BATCH)
            stroke.updateShape()
        } catch (exception: RuntimeException) {
            Log.e(TAG, "startStroke rejected input", exception)
            return
        }
        inProgressStroke = stroke
        bounds.union(firstInput.x, firstInput.y)
        bumpTick()
    }

    fun addInput(
        input: StrokeInput,
        bounds: RectF,
    ) {
        val stroke = inProgressStroke ?: return
        try {
            stroke.enqueueInputs(MutableStrokeInputBatch().add(input), EMPTY_BATCH)
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
            val record =
                StrokeRecord(
                    objectId = ObjectId(PenlyIds.newId()),
                    stroke = stroke.toImmutable(),
                    bounds = RectF(bounds),
                )
            val command = AddStroke(record)
            commands.push(command)
            command.redo(this)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "endStroke rejected stroke", exception)
            bumpTick()
        }
    }

    fun loadRecords(records: List<StrokeRecord>) {
        strokes.clear()
        commands = UndoRedoStack()
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
        command.undo(this)
    }

    fun redo() {
        val command = commands.redo() ?: return
        command.redo(this)
    }

    fun clearAll() {
        strokes.clear()
        objects = emptyList()
        images.clear()
        commands = UndoRedoStack()
        inProgressStroke = null
        clearSelection()
        onStrokesChanged?.invoke(strokes.toList())
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
            } ?: return
        val command =
            DeleteSelection(
                removedStrokes = listOf(hit),
                strokeIndices = listOf(strokes.indexOf(hit)),
                removedObjects = emptyList(),
                objectIndices = emptyList(),
            )
        commands.push(command)
        command.redo(this)
    }

    /** Inserts a text object at page position [at]; the action is undoable. */
    fun insertText(
        text: String,
        fontSize: Float,
        colorArgb: Int,
        at: Point,
    ) {
        if (text.isEmpty()) return
        val paint = android.graphics.Paint()
        paint.textSize = fontSize
        val width = paint.measureText(text)
        val now = System.currentTimeMillis()
        val objectText =
            TextObject(
                objectId = ObjectId(PenlyIds.newId()),
                bounds = Rect(at.x, at.y, at.x + width, at.y + fontSize),
                createdAtMillis = now,
                updatedAtMillis = now,
                text = text,
                fontSize = fontSize,
                colorArgb = colorArgb,
            )
        val command = InsertObjects(listOf(objectText))
        commands.push(command)
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
        commands.push(command)
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
        commands.push(command)
        command.redo(this)
        clearSelection()
    }

    /**
     * Clones every selected object: strokes keep the immutable [androidx.ink.strokes.Stroke]
     * instance (shared) but get a fresh id; page objects are copied with a fresh id and the
     * same payloadRef. The clones land at the identical position (no nudge).
     */
    fun copySelection() {
        if (selectedIds.isEmpty()) return
        val ids = selectedIds
        val clonedStrokes =
            strokes
                .filter { it.objectId in ids }
                .map { record -> record.copy(objectId = ObjectId(PenlyIds.newId())) }
        val clonedObjects =
            objects
                .filter { it.objectId in ids }
                .map { obj ->
                    val newId = ObjectId(PenlyIds.newId())
                    if (obj is ImageObject) {
                        images[obj.objectId]?.let { images[newId] = it }
                    }
                    obj.withObjectId(newId)
                }
        if (clonedStrokes.isNotEmpty() || clonedObjects.isNotEmpty()) {
            val command = InsertItems(clonedStrokes, clonedObjects)
            commands.push(command)
            command.redo(this)
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
        commands.push(MoveSelection(selectedIds.toList(), dx, dy))
        onStrokesChanged?.invoke(strokes.toList())
        bumpTick()
    }

    /**
     * Selects everything intersecting the lasso polygon built from [pagePoints]: strokes whose
     * effective bounds intersect the polygon or have any input point inside it, plus non-ink
     * objects whose bounds intersect. A degenerate polygon clears the selection.
     */
    fun selectLasso(pagePoints: List<Point>) {
        lassoPoints = emptyList()
        val path = LassoPath.of(pagePoints)
        if (path.bounds.isEmpty) {
            clearSelection()
            return
        }
        val ids = mutableSetOf<ObjectId>()
        for (record in strokes) {
            val bounds = record.bounds
            val intersects =
                path.intersects(
                    Rect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                )
            var pointInside = false
            for (index in 0 until record.stroke.inputs.size) {
                val input = record.stroke.inputs.get(index)
                val pt = record.transform.apply(Point(input.x, input.y))
                if (path.contains(pt)) {
                    pointInside = true
                    break
                }
            }
            if (intersects || pointInside) ids += record.objectId
        }
        for (obj in objects) {
            if (path.intersects(obj.bounds)) ids += obj.objectId
        }
        selectedIds = ids
        selectionBounds = computeSelectionBounds(ids)
        bumpTick()
    }

    /** True when the screen point lies inside the current selection bounds (page space). */
    fun hitTestSelection(
        screenX: Float,
        screenY: Float,
    ): Boolean {
        val bounds = selectionBounds ?: return false
        return bounds.contains(viewport.screenToPageX(screenX), viewport.screenToPageY(screenY))
    }

    private fun computeSelectionBounds(ids: Set<ObjectId>): Rect? {
        var union: Rect? = null
        for (record in strokes) {
            if (record.objectId !in ids) continue
            val bounds = record.bounds
            union =
                union?.union(Rect(bounds.left, bounds.top, bounds.right, bounds.bottom))
                    ?: Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }
        for (obj in objects) {
            if (obj.objectId in ids) {
                union = union?.union(obj.bounds) ?: obj.bounds
            }
        }
        return union
    }

    private fun clearSelection() {
        selectedIds = emptySet()
        selectionBounds = null
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

    private fun notifyPageChanged() {
        onStrokesChanged?.invoke(strokes.toList())
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
        val EMPTY_BATCH = MutableStrokeInputBatch()
        const val TAG: String = "InkCanvasState"
    }
}
