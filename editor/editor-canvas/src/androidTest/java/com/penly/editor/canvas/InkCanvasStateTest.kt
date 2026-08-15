package com.penly.editor.canvas

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.StrokeInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.penly.core.common.PenlyIds
import com.penly.core.geometry.Point
import com.penly.core.geometry.Rect
import com.penly.core.ink.PenTool
import com.penly.core.model.ImageObject
import com.penly.core.model.ObjectId
import com.penly.core.model.TextObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasStateTest {
    @Test
    fun toolSelectionAndMode_updatesState() {
        val state = InkCanvasState()
        assertEquals(PenTool.PEN, state.tool)
        assertFalse(state.selectionMode)

        state.selectTool(PenTool.HIGHLIGHTER)
        assertEquals(PenTool.HIGHLIGHTER, state.tool)

        state.setSelectionMode(true)
        assertTrue(state.selectionMode)

        state.setSelectionMode(false)
        assertFalse(state.selectionMode)
    }

    @Test
    fun strokeAddUndoRedoClear_operatesCorrectly() {
        val state = InkCanvasState()
        addSampleStroke(state, 10f, 20f, 30f, 40f)

        assertEquals(1, state.strokes.size)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)

        state.undo()
        assertEquals(0, state.strokes.size)
        assertFalse(state.canUndo)
        assertTrue(state.canRedo)

        state.redo()
        assertEquals(1, state.strokes.size)

        state.clearAll()
        assertEquals(0, state.strokes.size)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun textObjectInsertUndoRedo_operatesCorrectly() {
        val state = InkCanvasState()
        state.insertText(
            text = "Hello Penly",
            fontSize = 24f,
            colorArgb = 0xFF123456.toInt(),
            at = Point(50f, 60f),
        )

        assertEquals(1, state.objects.size)
        val textObj = state.objects.single() as TextObject
        assertEquals("Hello Penly", textObj.text)
        assertEquals(24f, textObj.fontSize, 0f)
        assertEquals(50f, textObj.bounds.left, 0f)
        assertEquals(60f, textObj.bounds.top, 0f)

        state.undo()
        assertEquals(0, state.objects.size)

        state.redo()
        assertEquals(1, state.objects.size)
    }

    @Test
    fun imageObjectInsertUndoRedo_operatesCorrectly() {
        val state = InkCanvasState()
        val objectId = ObjectId(PenlyIds.newId())
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val bounds = Rect(10f, 10f, 60f, 60f)

        state.insertImage(objectId, bounds, "image/png", "assets/sample.png")
        state.setImage(objectId, bitmap)

        assertEquals(1, state.objects.size)
        assertEquals(bitmap, state.images[objectId])

        state.undo()
        assertEquals(0, state.objects.size)

        state.redo()
        assertEquals(1, state.objects.size)
    }

    @Test
    fun lassoSelect_selectsStrokesAndObjects() {
        val state = InkCanvasState()
        addSampleStroke(state, 20f, 20f, 40f, 40f)
        state.insertText("Test", 20f, 0xFF000000.toInt(), Point(50f, 50f))
        state.insertImage(
            ObjectId(PenlyIds.newId()),
            Rect(200f, 200f, 300f, 300f),
            "image/png",
            null,
        )

        assertEquals(1, state.strokes.size)
        assertEquals(2, state.objects.size)

        // Lasso polygon enclosing (0,0) to (100,100) — includes stroke and text, excludes image.
        val polygon =
            listOf(
                Point(0f, 0f),
                Point(100f, 0f),
                Point(100f, 100f),
                Point(0f, 100f),
            )
        state.selectLasso(polygon)

        assertEquals(2, state.selectedIds.size)
        assertTrue(state.isSelected(state.strokes.first().objectId))
        assertTrue(state.isSelected(state.objects.first().objectId))
        assertFalse(state.isSelected(state.objects.last().objectId))
        assertNotNull(state.selectionBounds)
    }

    @Test
    fun selectionHitTest_worksAcrossViewportTransform() {
        val state = InkCanvasState()
        state.insertText("Target", 20f, 0xFF000000.toInt(), Point(50f, 50f))
        val textId = state.objects.first().objectId
        state.selectLasso(
            listOf(
                Point(40f, 40f),
                Point(100f, 40f),
                Point(100f, 100f),
                Point(40f, 100f),
            ),
        )
        assertTrue(state.isSelected(textId))

        // In identity viewport: screen (60, 60) is inside selection.
        assertTrue(state.hitTestSelection(60f, 60f))
        assertFalse(state.hitTestSelection(10f, 10f))

        // Pan viewport by +100 in X: screen (160, 60) maps to page (60, 60) which is inside.
        state.pan(100f, 0f)
        assertTrue(state.hitTestSelection(160f, 60f))
        assertFalse(state.hitTestSelection(60f, 60f))
    }

    @Test
    fun moveSelection_translatesObjectsAndSupportsUndoRedo() {
        val state = InkCanvasState()
        addSampleStroke(state, 10f, 10f, 30f, 30f)
        val stroke = state.strokes.first()

        state.selectLasso(
            listOf(
                Point(0f, 0f),
                Point(50f, 0f),
                Point(50f, 50f),
                Point(0f, 50f),
            ),
        )
        assertTrue(state.isSelected(stroke.objectId))

        state.moveSelection(20f, 30f)
        state.commitMove(20f, 30f)

        val movedStroke = state.strokes.first()
        assertEquals(20f, movedStroke.transform.translationX, 0.01f)
        assertEquals(30f, movedStroke.transform.translationY, 0.01f)

        state.undo()
        val undoneStroke = state.strokes.first()
        assertEquals(0f, undoneStroke.transform.translationX, 0.01f)
        assertEquals(0f, undoneStroke.transform.translationY, 0.01f)

        state.redo()
        val redoneStroke = state.strokes.first()
        assertEquals(20f, redoneStroke.transform.translationX, 0.01f)
        assertEquals(30f, redoneStroke.transform.translationY, 0.01f)
    }

    @Test
    fun copySelection_duplicatesWithNewIdsAndCopiesBitmaps() {
        val state = InkCanvasState()
        addSampleStroke(state, 10f, 10f, 30f, 30f)
        val imageId = ObjectId(PenlyIds.newId())
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        state.insertImage(imageId, Rect(10f, 10f, 50f, 50f), "image/png", "assets/sample.png")
        state.setImage(imageId, bitmap)

        state.selectLasso(
            listOf(
                Point(0f, 0f),
                Point(60f, 0f),
                Point(60f, 60f),
                Point(0f, 60f),
            ),
        )
        assertEquals(2, state.selectedIds.size)

        state.copySelection()
        assertEquals(2, state.strokes.size)
        assertEquals(2, state.objects.size)

        val clonedImage = state.objects.last() as ImageObject
        assertTrue(clonedImage.objectId != imageId)
        assertNotNull(state.images[clonedImage.objectId])
        assertEquals(bitmap, state.images[clonedImage.objectId])

        // Single undo reverses the whole copy action
        state.undo()
        assertEquals(1, state.strokes.size)
        assertEquals(1, state.objects.size)

        state.redo()
        assertEquals(2, state.strokes.size)
        assertEquals(2, state.objects.size)
    }

    @Test
    fun deleteSelection_removesItemsAndSupportsUndo() {
        val state = InkCanvasState()
        addSampleStroke(state, 10f, 10f, 30f, 30f)
        state.insertText("Delete Me", 20f, 0xFF000000.toInt(), Point(10f, 10f))

        state.selectLasso(
            listOf(
                Point(0f, 0f),
                Point(50f, 0f),
                Point(50f, 50f),
                Point(0f, 50f),
            ),
        )
        assertEquals(2, state.selectedIds.size)

        state.deleteSelection()
        assertEquals(0, state.strokes.size)
        assertEquals(0, state.objects.size)
        assertEquals(0, state.selectedIds.size)
        assertNull(state.selectionBounds)

        state.undo()
        assertEquals(1, state.strokes.size)
        assertEquals(1, state.objects.size)
    }

    @Test
    fun eraseAt_removesHitStrokeAndSupportsUndo() {
        val state = InkCanvasState()
        addSampleStroke(state, 50f, 50f, 70f, 70f)
        assertEquals(1, state.strokes.size)

        // Miss
        state.eraseAt(10f, 10f, 5f)
        assertEquals(1, state.strokes.size)

        // Hit
        state.eraseAt(60f, 60f, 15f)
        assertEquals(0, state.strokes.size)

        state.undo()
        assertEquals(1, state.strokes.size)
    }

    private fun addSampleStroke(
        state: InkCanvasState,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ) {
        val bounds = RectF()
        val input1 =
            StrokeInput().apply {
                update(
                    x = x1,
                    y = y1,
                    elapsedTimeMillis = 0L,
                    toolType = InputToolType.STYLUS,
                    pressure = 0.5f,
                )
            }
        val input2 =
            StrokeInput().apply {
                update(
                    x = x2,
                    y = y2,
                    elapsedTimeMillis = 16L,
                    toolType = InputToolType.STYLUS,
                    pressure = 0.8f,
                )
            }
        state.startStroke(PenTool.PEN, input1, bounds)
        state.addInput(input2, bounds)
        state.endStroke(bounds)
    }
}
