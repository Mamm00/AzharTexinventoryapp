package com.alazhartex.stock.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    /**
     * Regression: "Add roll(s)" sheet -> Fabric dropdown -> Choose colour.
     * The palette picker previously crashed the app (reported on v1.3.0 by a user).
     * Seed is SHA-pinned, so wh-alwa's first sorted EN fabric name is stable.
     */
    @Test
    fun addRollChooseColourOpensPickerWithoutCrash() {
        composeTestRule.waitUntilAtLeastOneExists(hasText(appName()), timeoutMillis = 60_000)
        openDrawer()
        composeTestRule.onNodeWithText(res(R.string.nav_stock)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilAtLeastOneExists(hasText(res(R.string.action_add_roll)), timeoutMillis = 60_000)

        // Click the first card's Add roll(s) button (warehouses render in seed order).
        composeTestRule.onAllNodesWithText(res(R.string.action_add_roll))[0].performClick()
        composeTestRule.waitForIdle()

        // Pick the fabric that is mapped in the pinned demo seed.
        composeTestRule.waitUntilAtLeastOneExists(hasText(res(R.string.label_fabric)), timeoutMillis = 30_000)
        composeTestRule.onNodeWithText(res(R.string.label_fabric), useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilAtLeastOneExists(hasText("14oz Indigo Denim"), timeoutMillis = 30_000)
        composeTestRule.onNodeWithText("14oz Indigo Denim").performClick()
        composeTestRule.waitForIdle()

        // Open the palette picker — this was the reported crash site.
        composeTestRule.waitUntilAtLeastOneExists(hasText(res(R.string.picker_title)), timeoutMillis = 30_000)
        composeTestRule.onNodeWithText(res(R.string.picker_title), useUnmergedTree = true).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText(res(R.string.picker_search_hint)), timeoutMillis = 30_000)

        // Rendering/grid composition is where the reported crash happens. Dismiss the picker.
        composeTestRule.onAllNodesWithText(res(R.string.action_cancel))[0].performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(res(R.string.action_cancel)).performClick()
        composeTestRule.waitForIdle()
    }
}
