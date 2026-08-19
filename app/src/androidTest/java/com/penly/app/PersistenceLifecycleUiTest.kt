package com.penly.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.penly.core.document.LoadResult
import com.penly.core.document.PenlyStore
import com.penly.core.model.InkObject
import com.penly.core.storage.FileContentStore
import com.penly.editor.canvas.INK_CANVAS_TAG
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Process-lifecycle workflows against the real [MainActivity] and its on-disk store:
 * the "draw, close, reopen, is it still there?" loop that manual testers repeat endlessly,
 * plus the crash-recovery UX.
 */
@RunWith(AndroidJUnit4::class)
class PersistenceLifecycleUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var penlyDir: File

    @Before
    fun clearAppStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        penlyDir = File(context.filesDir, "penly")
        penlyDir.deleteRecursively()
    }

    @After
    fun cleanUp() {
        penlyDir.deleteRecursively()
    }

    @Test
    fun drawThenRecreate_contentSurvivesProcessDeath() {
        composeRule.onNodeWithTag(INK_CANVAS_TAG).performTouchInput {
            swipe(Offset(100f, 300f), Offset(300f, 300f), durationMillis = 80L)
        }
        waitForSavedInk(1)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) {
            isUndoDisabled()
        }
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) { bannerAbsent() }
        assertEquals(1, savedInkCount())
    }

    private fun store(): PenlyStore = PenlyStore(FileContentStore(penlyDir))

    private fun savedInkCount(): Int =
        store()
            .listDocuments()
            .lastOrNull()
            ?.let { store().load(it) }
            ?.let { load ->
                if (load is LoadResult.Success) {
                    load.document
                        .pages
                        .firstOrNull()
                        ?.objects
                        ?.count { obj -> obj is InkObject }
                } else {
                    null
                }
            }
            ?: 0

    private fun waitForSavedInk(expected: Int) {
        composeRule.waitUntil(timeoutMillis = SAVE_TIMEOUT_MILLIS) {
            savedInkCount() == expected
        }
    }

    private fun isUndoDisabled(): Boolean {
        val config = composeRule.onNodeWithText("Undo").fetchSemanticsNode().config
        return config.contains(SemanticsProperties.Disabled)
    }

    private fun bannerAbsent(): Boolean {
        val nodes = composeRule.onAllNodesWithText("Recovered unsaved changes").fetchSemanticsNodes()
        return nodes.isEmpty()
    }

    private companion object {
        const val SAVE_TIMEOUT_MILLIS: Long = 10_000L
    }
}
