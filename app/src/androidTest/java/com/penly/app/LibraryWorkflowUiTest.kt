package com.penly.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.penly.app.ui.penlyApp
import com.penly.core.document.LoadResult
import com.penly.core.document.PenlyStore
import com.penly.core.model.PageTemplate
import com.penly.core.storage.InMemoryContentStore
import com.penly.editor.canvas.INK_CANVAS_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the Phase 5 first-run path: library → notebook → new template page → editor. */
@RunWith(AndroidJUnit4::class)
class LibraryWorkflowUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val store = PenlyStore(InMemoryContentStore())

    @Test
    fun createNotebook_thenGridPage_opensTheNewPage() {
        composeRule.setContent { penlyApp(store = store) }

        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("New notebook").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("New notebook").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Page 1").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("New page").performClick()
        composeRule.onNodeWithText("Grid").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            document()?.pages?.size == 2
        }
        assertEquals(PageTemplate.GRID, document()!!.pages.last().template)

        composeRule.onNodeWithText("Page 2").performClick()
        composeRule.onNodeWithTag(INK_CANVAS_TAG).assertExists()
    }

    private fun document() =
        store
            .listDocuments()
            .singleOrNull()
            ?.let(store::load)
            ?.let { result -> (result as? LoadResult.Success)?.document }

    private companion object {
        const val TIMEOUT_MILLIS: Long = 10_000L
    }
}
