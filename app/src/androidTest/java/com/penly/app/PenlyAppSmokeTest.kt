package com.penly.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun appShowsLibrary() {
        composeRule.setContent {
            penlyApp()
        }
        composeRule.onNodeWithText("Penly").assertExists()
        composeRule.onNodeWithText("New notebook").assertExists()
    }

    @Test
    fun appShowsLibraryControls() {
        composeRule.setContent {
            penlyApp()
        }
        composeRule.onNodeWithText("All").assertExists()
        composeRule.onNodeWithText("Recent").assertExists()
        composeRule.onNodeWithText("Favorites").assertExists()
        composeRule.onNodeWithText("Trash").assertExists()
    }

    @Test
    fun settingsOpensAndCloses() {
        composeRule.setContent {
            penlyApp()
        }
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Appearance").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Penly").assertExists()
    }
}
