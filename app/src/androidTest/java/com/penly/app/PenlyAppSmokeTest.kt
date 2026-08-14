package com.penly.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    fun appShowsTitle() {
        composeRule.setContent {
            penlyApp()
        }
        composeRule.onNodeWithText("Penly").assertExists()
    }
}
