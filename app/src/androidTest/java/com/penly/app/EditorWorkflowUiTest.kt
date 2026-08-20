package com.penly.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performGesture
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.penly.app.ui.penlyApp
import com.penly.core.document.InkObjectMapper
import com.penly.core.document.LoadResult
import com.penly.core.document.PenlyStore
import com.penly.core.model.InkObject
import com.penly.core.model.TextObject
import com.penly.core.storage.InMemoryContentStore
import com.penly.editor.canvas.INK_CANVAS_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end workflow tests: real user flows (draw, erase, undo/redo, dialogs, selection,
 * pinch-zoom, copy/paste) driven through the Compose UI against an in-memory [PenlyStore].
 * Every assertion is made against what the store actually persisted, so these catch the
 * save/reload wiring bugs manual testers hit — not just state in memory.
 */
@RunWith(AndroidJUnit4::class)
class EditorWorkflowUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val store = PenlyStore(InMemoryContentStore())
    private var generation by mutableIntStateOf(0)

    private fun launchApp() {
        composeRule.setContent {
            key(generation) {
                penlyApp(store = store)
            }
        }
    }

    private fun drawSwipe(
        from: Offset,
        to: Offset,
    ) {
        composeRule.onNodeWithTag(INK_CANVAS_TAG).performTouchInput {
            swipe(from, to, durationMillis = 80L)
        }
    }

    private fun savedInkCount(): Int =
        currentDocument()
            ?.pages
            ?.firstOrNull()
            ?.objects
            ?.count { it is InkObject }
            ?: 0

    private fun savedTextCount(): Int =
        currentDocument()
            ?.pages
            ?.firstOrNull()
            ?.objects
            ?.count { it is TextObject }
            ?: 0

    private fun firstInkObject(): InkObject? =
        currentDocument()
            ?.pages
            ?.firstOrNull()
            ?.objects
            ?.filterIsInstance<InkObject>()
            ?.firstOrNull()

    private fun currentDocument() =
        store
            .listDocuments()
            .lastOrNull()
            ?.let { store.load(it) }
            ?.let { if (it is LoadResult.Success) it.document else null }

    private fun waitForInkCount(expected: Int) {
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) {
            savedInkCount() == expected
        }
    }

    private fun isUndoDisabled(): Boolean {
        val config = composeRule.onNodeWithText("Undo").fetchSemanticsNode().config
        return config.contains(SemanticsProperties.Disabled)
    }

    @Test
    fun drawStroke_savesToStoreAndReloadsIdentically() {
        launchApp()
        drawSwipe(Offset(100f, 300f), Offset(300f, 300f))
        waitForInkCount(1)

        // "Reload" the app against the same store: the stroke must come back.
        generation++
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) { isUndoDisabled() }
        assertEquals(1, savedInkCount())
    }

    @Test
    fun rapidStrokes_saveStormLosesNothing() {
        launchApp()
        repeat(3) { index ->
            drawSwipe(Offset(80f, 250f + index * 60f), Offset(320f, 250f + index * 60f))
        }
        waitForInkCount(3)
    }

    @Test
    fun undoRedo_roundTripThroughButtons() {
        launchApp()
        drawSwipe(Offset(100f, 300f), Offset(300f, 300f))
        waitForInkCount(1)

        composeRule.onNodeWithText("Undo").performClick()
        waitForInkCount(0)

        composeRule.onNodeWithText("Redo").performClick()
        waitForInkCount(1)
    }

    @Test
    fun eraserSwipe_removesStrokeAndUndoRestores() {
        launchApp()
        drawSwipe(Offset(100f, 300f), Offset(300f, 300f))
        waitForInkCount(1)

        composeRule.onNodeWithText("Eraser").performClick()
        drawSwipe(Offset(80f, 300f), Offset(320f, 300f))
        waitForInkCount(0)

        composeRule.onNodeWithText("Undo").performClick()
        waitForInkCount(1)
    }

    @Test
    fun clearDialog_cancelKeepsContent() {
        launchApp()
        drawSwipe(Offset(100f, 300f), Offset(300f, 300f))
        waitForInkCount(1)

        composeRule.onNodeWithText("Clear").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(1, savedInkCount())
    }

    @Test
    fun clearDialog_confirmClearsAndResetsUndoHistory() {
        launchApp()
        drawSwipe(Offset(100f, 300f), Offset(300f, 300f))
        waitForInkCount(1)

        composeRule.onNodeWithText("Clear").performClick()
        // The top bar button and the dialog confirm button share the label. Dialogs are
        // composed in their own root after the main root, so index 1 is the dialog's button.
        composeRule.onAllNodesWithText("Clear")[1].performClick()
        waitForInkCount(0)

        // Current contract: Clear resets the undo history, so Undo is disabled.
        assertTrue("Undo must be disabled after Clear", isUndoDisabled())
    }

    @Test
    fun textDialog_okAddsTextAndUndoRemovesIt() {
        launchApp()
        composeRule.onNodeWithText("Text").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()
        assertEquals(0, savedTextCount())

        composeRule.onNodeWithText("Text").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Hello Penly")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) { savedTextCount() == 1 }
        val textObject =
            currentDocument()!!
                .pages
                .first()
                .objects
                .filterIsInstance<TextObject>()
                .first()
        assertEquals("Hello Penly", textObject.text)

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) { savedTextCount() == 0 }
    }

    @Test
    fun textDialog_cancelAddsNothing() {
        launchApp()
        composeRule.onNodeWithText("Text").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("will be discarded")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertEquals(0, savedTextCount())
    }

    @Test
    fun crossTypeUndo_strokeThenTextUndoIndependently() {
        launchApp()
        drawSwipe(Offset(100f, 300f), Offset(300f, 300f))
        waitForInkCount(1)
        composeRule.onNodeWithText("Text").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("note")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) { savedTextCount() == 1 }

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) { savedTextCount() == 0 }
        assertEquals(1, savedInkCount())

        composeRule.onNodeWithText("Undo").performClick()
        waitForInkCount(0)
    }

    @Test
    fun lassoSelect_deleteAndUndoRestores() {
        launchApp()
        drawSwipe(Offset(100f, 300f), Offset(300f, 300f))
        waitForInkCount(1)

        composeRule.onNodeWithText("Select").performClick()
        composeRule.onNodeWithTag(INK_CANVAS_TAG).performTouchInput {
            down(Offset(80f, 240f))
            moveTo(Offset(320f, 240f))
            moveTo(Offset(320f, 440f))
            moveTo(Offset(80f, 440f))
            up()
        }
        composeRule.onNodeWithText("Delete").performClick()
        waitForInkCount(0)

        composeRule.onNodeWithText("Undo").performClick()
        waitForInkCount(1)
    }

    @Test
    fun lassoSelect_moveSelectionPersistsAndUndoes() {
        launchApp()
        drawSwipe(Offset(100f, 300f), Offset(300f, 300f))
        waitForInkCount(1)

        composeRule.onNodeWithText("Select").performClick()
        composeRule.onNodeWithTag(INK_CANVAS_TAG).performTouchInput {
            down(Offset(80f, 240f))
            moveTo(Offset(320f, 240f))
            moveTo(Offset(320f, 440f))
            moveTo(Offset(80f, 440f))
            up()
        }
        composeRule.onNodeWithTag(INK_CANVAS_TAG).performTouchInput {
            down(Offset(200f, 300f))
            moveBy(Offset(50f, 30f))
            up()
        }
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) {
            firstInkObject()?.transform?.translationX == 50f
        }
        assertEquals(30f, firstInkObject()!!.transform.translationY, 0.1f)

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) {
            firstInkObject()?.transform == com.penly.core.geometry.Transform.IDENTITY
        }
    }

    @Test
    fun pinchZoom_lassoMove_convertsDragDeltaToPageUnits() {
        launchApp()
        val canvasCenter = Offset(205f, 390f)
        composeRule.onNodeWithTag(INK_CANVAS_TAG).performGesture {
            pinch(
                start0 = canvasCenter - Offset(50f, 0f),
                end0 = canvasCenter - Offset(150f, 0f),
                start1 = canvasCenter + Offset(50f, 0f),
                end1 = canvasCenter + Offset(150f, 0f),
            )
        }
        drawSwipe(Offset(100f, 300f), Offset(300f, 300f))
        waitForInkCount(1)

        composeRule.onNodeWithText("Select").performClick()
        composeRule.onNodeWithTag(INK_CANVAS_TAG).performTouchInput {
            down(Offset(80f, 240f))
            moveTo(Offset(320f, 240f))
            moveTo(Offset(320f, 440f))
            moveTo(Offset(80f, 440f))
            up()
        }
        // Drag the selection by 60 screen px at 3x zoom: exactly 20 page units.
        composeRule.onNodeWithTag(INK_CANVAS_TAG).performTouchInput {
            down(Offset(200f, 300f))
            moveBy(Offset(60f, 0f))
            up()
        }
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) {
            firstInkObject()?.transform?.translationX == 20f
        }
        assertEquals(0f, firstInkObject()!!.transform.translationY, 0.1f)

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) {
            firstInkObject()?.transform == com.penly.core.geometry.Transform.IDENTITY
        }
    }

    @Test
    fun cutPaste_duplicatesContentAndUndoRestores() {
        launchApp()
        drawSwipe(Offset(100f, 300f), Offset(300f, 300f))
        waitForInkCount(1)

        composeRule.onNodeWithText("Select").performClick()
        composeRule.onNodeWithTag(INK_CANVAS_TAG).performTouchInput {
            down(Offset(80f, 240f))
            moveTo(Offset(320f, 240f))
            moveTo(Offset(320f, 440f))
            moveTo(Offset(80f, 440f))
            up()
        }
        composeRule.onNodeWithText("Cut").performClick()
        waitForInkCount(0)

        composeRule.onNodeWithText("Paste").performClick()
        waitForInkCount(1)

        // First undo removes the pasted clone; the second restores the cut original.
        composeRule.onNodeWithText("Undo").performClick()
        waitForInkCount(0)
        composeRule.onNodeWithText("Undo").performClick()
        waitForInkCount(1)
    }

    @Test
    fun pinchZoom_thenDraw_landsStrokeAtCorrectPageCoordinates() {
        launchApp()
        val canvasCenter = Offset(205f, 390f)
        composeRule.onNodeWithTag(INK_CANVAS_TAG).performGesture {
            pinch(
                start0 = canvasCenter - Offset(50f, 0f),
                end0 = canvasCenter - Offset(150f, 0f),
                start1 = canvasCenter + Offset(50f, 0f),
                end1 = canvasCenter + Offset(150f, 0f),
            )
        }
        drawSwipe(Offset(100f, 300f), Offset(300f, 300f))
        waitForInkCount(1)

        // Pinch math: scale 100 -> 300 (factor 3), centroid fixed at (205, 390), so
        // offset = 205 - 205*3 = -410 for X and 390 - 390*3 = -780 for Y; page = (screen + offset) / 3.
        val record = InkObjectMapper.toStrokeRecord(firstInkObject()!!)!!
        val firstInput = record.stroke.inputs.get(0)
        val lastInput = record.stroke.inputs.get(record.stroke.inputs.size - 1)
        assertEquals((100f + 410f) / 3f, firstInput.x, PAGE_TOLERANCE)
        assertEquals((300f + 780f) / 3f, firstInput.y, PAGE_TOLERANCE)
        assertEquals((300f + 410f) / 3f, lastInput.x, PAGE_TOLERANCE)
        assertEquals((300f + 780f) / 3f, lastInput.y, PAGE_TOLERANCE)
    }

    @Test
    fun twoFingerGesture_abortsInProgressStroke() {
        launchApp()
        composeRule.onNodeWithTag(INK_CANVAS_TAG).performGesture {
            down(pointerId = 0, position = Offset(100f, 300f))
            moveBy(pointerId = 0, delta = Offset(50f, 0f))
            down(pointerId = 1, position = Offset(205f, 390f))
            moveBy(pointerId = 0, delta = Offset(50f, 0f))
            moveBy(pointerId = 1, delta = Offset(20f, 0f))
            up(pointerId = 1)
            up(pointerId = 0)
        }
        composeRule.waitForIdle()

        // The aborted stroke must never reach the store.
        assertEquals(0, savedInkCount())
        assertTrue("Undo must be disabled: no stroke was committed", isUndoDisabled())
    }

    private companion object {
        const val SAVE_TIMEOUT_MILLIS: Long = 10_000L
        const val PAGE_TOLERANCE: Float = 15f
    }
}
