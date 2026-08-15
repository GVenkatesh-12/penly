package com.penly.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.penly.app.ui.penlyApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenlyAppSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appShowsEditor() {
        composeRule.setContent {
            penlyApp()
        }
        composeRule.onNodeWithText("Page 1").assertExists()
    }

    @Test
    fun appShowsEditorControls() {
        composeRule.setContent {
            penlyApp()
        }
        composeRule.onNodeWithText("Undo").assertExists()
        composeRule.onNodeWithText("Redo").assertExists()
        composeRule.onNodeWithText("Clear").assertExists()
        composeRule.onNodeWithText("Text").assertExists()
        composeRule.onNodeWithText("Select").assertExists()
        composeRule.onNodeWithText("Pen").assertExists()
    }

    @Test
    fun textDialog_opensAndDismisses() {
        composeRule.setContent {
            penlyApp()
        }
        composeRule.onNodeWithText("Text").performClick()
        composeRule.onNodeWithText("Add text").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Add text").assertDoesNotExist()
    }
}
