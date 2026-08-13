package com.edukasyon.studentai

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboarding_showsWelcomeOrHome() {
        composeRule.waitForIdle()
        // Fresh install shows onboarding; returning users may land on home.
        val hasWelcome = composeRule.onAllNodesWithText("Welcome to StudentAI").fetchSemanticsNodes().isNotEmpty()
        val hasHome = composeRule.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty()
        assert(hasWelcome || hasHome)
    }
}
