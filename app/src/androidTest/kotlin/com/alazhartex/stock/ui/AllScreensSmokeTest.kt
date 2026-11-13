package com.alazhartex.stock.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.MainActivity
import com.alazhartex.stock.R
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
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

    /**
     * Fresh installs ship BLANK (accounts only, business tables empty — see SeedLoader), so the
     * Stock cards never render unless the pinned demo dataset is (re)applied first. Reload it
     * before the suite, exactly like DebugDangerZoneTest does.
     */
    companion object {
        @BeforeClass
        @JvmStatic
        fun resetToDemoDataset() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AlAzharApp
            runBlocking {
                app.container.debugRepository.reloadDemoData("instrumentation-smoke")
            }
        }
    }

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
     * Regression: "Add roll(s)" -> Fabric dropdown -> inline colour list.
     * Wh-alwa's 14oz Indigo Denim has exactly one mapping in the pinned demo seed (C036),
     * so the inline list must (a) render, (b) select a mapped colour, (c) assign an
     * unmapped colour (C012) to the fabric on the spot and auto-select it.
     */
    @Test
    fun addRollInlineColourSelectAndAssign() {
        composeTestRule.waitUntilAtLeastOneExists(hasText(appName()), timeoutMillis = 60_000)
        openDrawer()
        composeTestRule.onNodeWithText(res(R.string.nav_stock)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilAtLeastOneExists(hasText(res(R.string.action_add_roll)), timeoutMillis = 60_000)

        // Target the Al-Waha card deterministically (wh-625's single mapped fabric would
        // otherwise break the pinned-fabric assertion).
        composeTestRule.onNodeWithTag("stock_add_wh-alwa").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Pick the fabric that is mapped in the pinned demo seed.
        composeTestRule.waitUntilAtLeastOneExists(hasText(res(R.string.label_fabric)), timeoutMillis = 30_000)
        composeTestRule.onNodeWithTag("stock_fabric_field").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilAtLeastOneExists(hasText("14oz Indigo Denim"), timeoutMillis = 30_000)
        composeTestRule.onNodeWithText("14oz Indigo Denim").performClick()
        composeTestRule.waitForIdle()

        // Inline colour list renders inside the dialog (no overlay). wh-alwa's 14oz Indigo
        // Denim has exactly one mapping in the pinned demo seed: C036 (Bright Amber). The
        // list is a LazyColumn — rows off-screen are not composed, so filter with the
        // search field before asserting on a row.
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("colour_search"), timeoutMillis = 30_000)
        composeTestRule.onNodeWithTag("colour_search").performTextInput("C036")
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("colour_row_C036"), timeoutMillis = 30_000)

        // Tap the mapped colour row — it must select, no crash, summary shows the code.
        composeTestRule.onNodeWithTag("colour_row_C036").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("colour_summary"), timeoutMillis = 30_000)
        composeTestRule.onNodeWithTag("colour_summary", useUnmergedTree = true).assertIsDisplayed()

        // The fabric's palette at wh-alwa does NOT include C012 (Light Rose). Adding it to
        // the fabric from the inline list must create the mapping and auto-select it.
        composeTestRule.onNodeWithTag("colour_search").performTextClearance()
        composeTestRule.onNodeWithTag("colour_search").performTextInput("C012")
        composeTestRule.waitUntilAtLeastOneExists(hasTestTag("colour_add_C012"), timeoutMillis = 30_000)
        composeTestRule.onNodeWithTag("colour_add_C012").performClick()

        // After the add: the row flips to mapped (add button gone) and C012 is selected.
        composeTestRule.waitUntilDoesNotExist(hasTestTag("colour_add_C012"), timeoutMillis = 30_000)
        val summaryText = composeTestRule.onNodeWithTag("colour_summary", useUnmergedTree = true)
            .fetchSemanticsNode().config
            .getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
            .joinToString(" ") { it.text }
        assert(summaryText.contains("C012")) { "Expected colour_summary to show C012 after assignment, was: $summaryText" }

        // Close the dialog.
        composeTestRule.onAllNodesWithText(res(R.string.action_cancel))[0].performClick()
        composeTestRule.waitForIdle()
    }
}
