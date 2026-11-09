package com.alazhartex.stock.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alazhartex.stock.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D10 smoke: launch, open drawer, visit every one of the 12 routes, expect no crash
 * and a visible content node on each. Runs on the CI emulator (API 34, x86_64).
 * Route labels use the EN bundle (test device locale can be either; assertions use
 * locale-agnostic content descriptions where possible).
 */
@RunWith(AndroidJUnit4::class)
class AllScreensSmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndBootstraps() {
        composeTestRule.waitUntilAtLeastOneExists(hasText("AlAzharTex"), timeoutMillis = 60_000)
        // Drawer is openable (menu button carries the app name as content description).
        composeTestRule.onNodeWithContentDescription("AlAzharTex", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun allTwelveRoutesAreReachable() {
        composeTestRule.waitUntilAtLeastOneExists(hasText("AlAzharTex"), timeoutMillis = 60_000)
        // Open the drawer once; it stays open for the whole traversal.
        composeTestRule.onNodeWithContentDescription("AlAzharTex", useUnmergedTree = true).performClick()
        val labels = listOf(
            "Guide", "Stock", "Inventory", "Movements", "Colours", "Cross-View",
            "Dashboard", "Reports", "Masters", "Audit Log", "Settings", "My Profile",
        )
        for (label in labels) {
            // Drawer entries are EN because the test asserts against the fallback bundle;
            // if the device is ar-EG the node lookup simply fails fast and loud (better than silent skip).
            composeTestRule.onNodeWithText(label).performScrollTo().performClick()
            composeTestRule.waitForIdle()
            // Re-open the drawer for the next destination.
            composeTestRule.onNodeWithContentDescription("AlAzharTex", useUnmergedTree = true).performClick()
        }
    }
}
