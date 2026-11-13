package com.alazhartex.stock.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntil
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.MainActivity
import com.alazhartex.stock.R
import com.alazhartex.stock.data.local.DbProvider
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.database.sqlite.SQLiteDatabase

/**
 * v1.3.6 regression: an external writer (another process/tool edits the DB file directly)
 * used to leave the app showing stale roll counts forever — nothing invalidated the Compose
 * loaders keyed on `Unit`. The app must re-read from disk once DbProvider.refresh() runs.
 *
 * Scenario: the Stock grid is showing its cached cards; a roll is INSERTed behind the app's
 * back through a raw SQLite connection; DbProvider.refresh() is called; the Stock grid must
 * re-read and show the full active-roll count again.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DbRefreshTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        @BeforeClass
        @JvmStatic
        fun resetToDemoDataset() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AlAzharApp
            runBlocking {
                app.container.debugRepository.reloadDemoData("instrumentation-refresh")
            }
        }
    }

    private fun res(id: Int): String = composeTestRule.activity.getString(id)

    private fun appName(): String = res(R.string.app_name)

    private fun openDrawer() {
        composeTestRule.onNodeWithContentDescription(appName(), useUnmergedTree = true).performClick()
    }

    @Test
    fun stockGridPicksUpExternalInsertAfterDbProviderRefresh() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        openDrawer()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_stock").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilAtLeastOneExists(
            hasText(res(R.string.label_active_rolls), substring = true),
            timeoutMillis = 60_000,
        )
        val before = activeRollTotal()

        val mappingId = runBlocking {
            DbProvider.ensure(context).mappingDao().getActive().first().id
        }
        val rollId = "roll-refresh-test-${System.currentTimeMillis()}"
        val dbPath = context.getDatabasePath("alazhartex.db").absolutePath

        // Write behind the app's back: raw SQLite, no Room, no invalidation tracker.
        val external = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            external.execSQL(
                "INSERT INTO rolls (id, mapping_id, state, count) VALUES (?, ?, 'ACTIVE', ?)",
                arrayOf<Any>(rollId, mappingId, 2),
            )
        } finally {
            external.close()
        }

        DbProvider.refresh(context)

        composeTestRule.waitUntil(timeoutMillis = 30_000) { activeRollTotal() == before + 2 }

        // cleanup: drop the synthetic roll again
        val cleanup = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            cleanup.execSQL("DELETE FROM rolls WHERE id = ?", arrayOf<Any>(rollId))
        } finally {
            cleanup.close()
        }
        DbProvider.refresh(context)
        composeTestRule.waitUntil(timeoutMillis = 30_000) { activeRollTotal() == before }
    }

    private fun normalizeDigits(input: String): String = buildString {
        for (ch in input) {
            when (ch) {
                in '\u0660'..'\u0669' -> append('0' + (ch.code - 0x0660))
                in '\u06F0'..'\u06F9' -> append('0' + (ch.code - 0x06F0))
                else -> append(ch)
            }
        }
    }

    private fun activeRollTotal(): Long =
        composeTestRule.onAllNodesWithText(res(R.string.label_active_rolls), substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .sumOf { node ->
                val raw = node.config
                    .getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }
                    .orEmpty()
                    .joinToString(" ") { it.text }
                Regex("(\\d[\\d,]*)\\s*/").find(normalizeDigits(raw))
                    ?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: 0L
            }
}
