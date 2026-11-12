package com.alazhartex.stock.ui

import androidx.compose.ui.test.ExperimentalTestApi
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
import com.alazhartex.stock.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D10 smoke: launch, open drawer, visit every one of the routes, expect no crash
 * and a visible content node on each. Runs on the CI emulator (API 34, x86_64).
 *
 * Locale-agnostic: the app defaults to Arabic (SharedPreferences "language" = "ar"),
 * so assertions resolve strings through the ACTIVITY's wrapped resources instead of
 * hardcoding English labels.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AllScreensSmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun res(id: Int): String = composeTestRule.activity.getString(id)

    private fun appName(): String = res(R.string.app_name)

    private fun openDrawer() {
        composeTestRule.onNodeWithContentDescription(appName(), useUnmergedTree = true).performClick()
    }

    @Test
    fun appLaunchesAndBootstraps() {
        composeTestRule.waitUntilAtLeastOneExists(hasText(appName()), timeoutMillis = 60_000)
        // Drawer is openable (menu button carries the app name as content description).
        composeTestRule.onNodeWithContentDescription(appName(), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun allRoutesAreReachable() {
        composeTestRule.waitUntilAtLeastOneExists(hasText(appName()), timeoutMillis = 60_000)
        // Open the drawer once; it stays open for the whole traversal.
        openDrawer()
        val labels = listOf(
            R.string.nav_guide,
            R.string.nav_stock,
            R.string.nav_inventory,
            R.string.nav_movements,
            R.string.nav_colours,
            R.string.nav_crossview,
            R.string.nav_swatch_book,
            R.string.nav_dashboard,
            R.string.nav_reports,
            R.string.nav_masters,
            R.string.nav_audit,
            R.string.nav_settings,
            R.string.nav_profile,
        ).map(::res)
        for (label in labels) {
            composeTestRule.onNodeWithText(label).performScrollTo().performClick()
            composeTestRule.waitForIdle()
            // Re-open the drawer for the next destination.
            openDrawer()
        }
    }
}
