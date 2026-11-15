package com.alazhartex.stock.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.alazhartex.stock.AlAzharApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * v1.7.0 regression: "exporting db still not working". The share-sheet-only path could
 * deliver nothing anywhere. The export must (a) produce a consistent SQLite file whose
 * sha256 matches the sidecar, and (b) land a byte-identical copy in shared Downloads
 * (MediaStore on API 29+) without any receiving-app cooperation.
 */
@RunWith(AndroidJUnit4::class)
class ExportFlowTest {

    @get:Rule
    val grantNotifications: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    companion object {
        @BeforeClass
        @JvmStatic
        fun resetToDemoDataset() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AlAzharApp
            runBlocking { app.container.debugRepository.reloadDemoData("instrumentation-export") }
        }
    }

    @Test
    fun exportRoundTripWritesConsistentFileAndDownloadsCopy() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AlAzharApp
        val result = app.container.exportImportManager.exportDatabase()
        try {
            assertTrue("export file missing or empty", result.file.exists() && result.file.length() > 0)
            assertEquals("sidecar hex length", 64, result.sha256Hex.length)

            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(result.file).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    digest.update(buffer, 0, n)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            assertEquals("sha256 sidecar mismatch", result.sha256Hex, actual)

            // The export must open as a consistent SQLite database.
            android.database.sqlite.SQLiteDatabase.openDatabase(
                result.file.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            ).use { check ->
                check.rawQuery("PRAGMA integrity_check", null).use { c ->
                    c.moveToFirst()
                    assertEquals("ok", c.getString(0))
                }
            }

            // Automatic save-to-Downloads: byte-identical copy must exist.
            val saved = app.container.exportImportManager.saveToDownloads(result)
            assertNotNull("saveToDownloads returned null", saved)
            when (val uri = saved!!.uri) {
                null -> {
                    val legacy = File(saved.legacyPath!!)
                    assertTrue("legacy Downloads copy missing", legacy.exists())
                    assertArrayEquals(result.file.readBytes(), legacy.readBytes())
                    legacy.delete()
                    File(legacy.parentFile, result.sidecar.name).delete()
                }
                else -> {
                    val fromStore = InstrumentationRegistry.getInstrumentation().targetContext
                        .contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    assertArrayEquals("MediaStore copy differs from export", result.file.readBytes(), fromStore)
                    assertEquals(1, InstrumentationRegistry.getInstrumentation().targetContext.contentResolver.delete(uri, null, null))
                    // The .sha256 sidecar copy in Downloads is left behind (MediaStore insert already done by the app; ~100 bytes).
                }
            }
        } finally {
            result.file.delete()
            result.sidecar.delete()
        }
    }
}
