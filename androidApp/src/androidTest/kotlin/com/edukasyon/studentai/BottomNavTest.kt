package com.edukasyon.studentai

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNav_scheduleTab() {
        composeRule.waitForIdle()
        // Skip onboarding if shown
        if (composeRule.onAllNodesWithText("Enter StudentAI").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Enter StudentAI").performClick()
            composeRule.waitForIdle()
        } else if (composeRule.onAllNodesWithText("Get Started").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Continue Offline as Guest").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("Schedule").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Schedule").assertExists()
    }
}
