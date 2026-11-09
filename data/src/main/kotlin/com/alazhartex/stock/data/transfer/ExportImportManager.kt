package com.alazhartex.stock.data.transfer

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.alazhartex.stock.data.local.AppDatabase
import com.alazhartex.stock.data.local.ManifestEntity
import com.alazhartex.stock.data.repo.WriteContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * DB export/import (spec §9.8/§9.9).
 *  - Export: PRAGMA wal_checkpoint(TRUNCATE) -> _manifest rewrite -> VACUUM INTO -> sha256 sidecar.
 *  - Import: 5-rung ladder (R1 hash+sidecar, R2 SQLite magic, R3 PRAGMA integrity_check,
 *    R4 application_id+user_version+FK re-check, R5 per-table manifest counts) -> preview ->
 *    explicit overwrite confirmation -> safety copy (keep last 3) -> atomic replace.
 *  - NEVER an unguarded file copy of the live DB (§9.9); NEVER a 1-rung "trust me" import (§9.8).
 */
class ExportImportManager(
    private val context: Context,
    private val database: AppDatabase,
    private val wallClock: () -> Long,
    private val appVersionName: String,
) {
    data class ExportResult(val file: File, val sidecar: File, val sha256Hex: String, val bytes: Long)

    sealed interface ImportReport {
        data class RungFailed(val rung: Int, val detail: String) : ImportReport
        data class Ready(val preview: ImportPreview) : ImportReport
    }

    data class ImportPreview(val counts: Map<String, Long>, val appVersion: String, val schemaVersion: Long)

    private val exportsDir: File get() = File(context.filesDir, "exports").apply { mkdirs() }
    private val backupsDir: File get() = File(context.filesDir, "backups").apply { mkdirs() }
    private val stagingDir: File get() = File(context.filesDir, "import-staging").apply { mkdirs() }

    fun exportsDirectory(): File = exportsDir

    // ---------------- export ----------------
    suspend fun exportDatabase(): ExportResult {
        rewriteManifest()
        database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        val ts = fileTimestamp()
        val out = File(exportsDir, "alazhartex-export-$ts.db")
        if (out.exists()) out.delete()
        val escaped = out.absolutePath.replace("'", "''")
        database.openHelper.writableDatabase.execSQL("VACUUM INTO '$escaped'")
        val sidecar = File(exportsDir, out.name + ".sha256")
        val hashHex = sha256Of(out)
        sidecar.writeText("$hashHex  ${out.name}\n", Charsets.US_ASCII)
        return ExportResult(out, sidecar, hashHex, out.length())
    }

    private suspend fun rewriteManifest() {
        val ctx = WriteContext("system", UUID.randomUUID().toString(), "export.manifest", wallClock())
        val tables = listOf(
            "accounts", "warehouses", "shed_assignments", "fabrics", "colours", "mappings",
            "inventory", "movements", "rolls", "audit_log", "sessions", "app_settings",
        )
        val counts = tables.map { table ->
            val cursor = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table")
            val count = cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
            table to count
        }.toMap()
        database.withTransaction {
            database.manifestDao().clear(ctx)
            database.manifestDao().insertAll(
                counts.map { (table, count) -> ManifestEntity(table, count, 1, appVersionName, wallClock()) },
                ctx,
            )
        }
    }

    // ---------------- import ladder ----------------
    fun runLadder(candidate: File, sidecar: File?): ImportReport {
        // R1 — sha256 matches sidecar ("<hex>  <filename>\n")
        val expected = sidecar?.takeIf { it.isFile }?.readText(Charsets.US_ASCII)
            ?.lineSequence()?.firstOrNull()?.trim()?.split(Regex("\\s+"))?.firstOrNull()
            ?: return ImportReport.RungFailed(1, "sidecar_missing_or_unreadable")
        if (!expected.matches(Regex("[0-9a-fA-F]{64}"))) return ImportReport.RungFailed(1, "sidecar_malformed")
        val actual = sha256Of(candidate)
        if (!actual.equals(expected, ignoreCase = true)) return ImportReport.RungFailed(1, "sha256_mismatch")

        // R2 — SQLite header magic
        FileInputStream(candidate).use { input ->
            val header = ByteArray(16)
            val read = input.read(header)
            val magic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
            if (read < 16 || !header.copyOfRange(0, 16).contentEquals(magic)) {
                return ImportReport.RungFailed(2, "sqlite_magic_missing")
            }
        }

        // R3 — PRAGMA integrity_check on the imported copy (BEFORE any swap)
        val db = SQLiteDatabase.openDatabase(candidate.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val integrity = querySingleString(db, "PRAGMA integrity_check")
            if (integrity != "ok") return ImportReport.RungFailed(3, "integrity_check=$integrity")

            // R4 — application_id, user_version (Room-managed), FK re-check
            val appId = querySingleLong(db, "PRAGMA application_id")
            if (appId != 1096438856L) return ImportReport.RungFailed(4, "application_id_mismatch=$appId")
            val userVersion = querySingleLong(db, "PRAGMA user_version")
            if (userVersion != 1L) return ImportReport.RungFailed(4, "schema_version_mismatch=$userVersion")
            val fkViolations = queryRowCount(db, "PRAGMA foreign_key_check")
            if (fkViolations != 0L) return ImportReport.RungFailed(4, "foreign_key_violations=$fkViolations")

            // R5 — per-table row counts match _manifest
            val manifest = readManifest(db)
            if (manifest == null) return ImportReport.RungFailed(5, "manifest_table_missing")
            val candidates = listOf(
                "accounts", "warehouses", "shed_assignments", "fabrics", "colours", "mappings",
                "inventory", "movements", "rolls", "audit_log", "sessions", "app_settings",
            )
            for (table in candidates) {
                val expectedCount = manifest[table] ?: return ImportReport.RungFailed(5, "manifest_missing_table=$table")
                val actualCount = querySingleLong(db, "SELECT COUNT(*) FROM $table")
                if (actualCount != expectedCount) {
                    return ImportReport.RungFailed(5, "count_mismatch table=$table actual=$actualCount manifest=$expectedCount")
                }
            }
            val appVersion = querySingleStringOrNull(db, "SELECT app_version FROM _manifest LIMIT 1") ?: "unknown"
            return ImportReport.Ready(ImportPreview(manifest, appVersion, userVersion))
        } finally {
            db.close()
        }
    }

    private fun readManifest(db: SQLiteDatabase): Map<String, Long>? = try {
        val cursor: Cursor = db.rawQuery("SELECT table_name, row_count FROM _manifest", null)
        cursor.use { c ->
            val out = LinkedHashMap<String, Long>()
            while (c.moveToNext()) out[c.getString(0)] = c.getLong(1)
            out
        }
    } catch (e: android.database.SQLException) {
        null
    }

    private fun querySingleString(db: SQLiteDatabase, sql: String): String? =
        db.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun querySingleStringOrNull(db: SQLiteDatabase, sql: String): String? =
        try {
            querySingleString(db, sql)
        } catch (e: android.database.SQLException) {
            null
        }

    private fun querySingleLong(db: SQLiteDatabase, sql: String): Long =
        db.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }

    private fun queryRowCount(db: SQLiteDatabase, sql: String): Long =
        db.rawQuery(sql, null).use { c -> c.count.toLong() }

    /**
     * Destructive step — call ONLY after runLadder() == Ready AND explicit user confirmation.
     * Safety copy first; keeps the 3 most recent; prune decisions are logged outside the DB.
     * Returns the backup file; the caller must close/recreate the Room instance and relaunch.
     */
    fun safetyBackupAndReplace(currentDb: File, candidate: File): File {
        pruneBackups()
        val ts = fileTimestamp()
        val safety = File(backupsDir, "safety-$ts.db")
        currentDb.copyTo(safety, overwrite = true)
        val tmp = File(currentDb.parentFile, currentDb.name + ".importing")
        candidate.copyTo(tmp, overwrite = true)
        // Atomic replace within the same directory
        if (!tmp.renameTo(currentDb)) {
            currentDb.delete()
            check(tmp.renameTo(currentDb)) { "import_swap_failed" }
        }
        return safety
    }

    private fun pruneBackups() {
        val existing = backupsDir.listFiles { f -> f.isFile && f.name.startsWith("safety-") && f.name.endsWith(".db") }
            ?.sortedByDescending { it.lastModified() } ?: return
        val doomed = existing.drop(3).also { list ->
            if (list.isNotEmpty()) {
                File(backupsDir, "prune.log").appendText(
                    list.joinToString("\n") { "pruned ${it.name} at ${wallClock()}" } + "\n",
                    Charsets.UTF_8,
                )
            }
        }
        for (file in doomed) file.delete()
    }

    fun stageImport(inputStream: java.io.InputStream, isSidecar: Boolean): File {
        val safe = if (isSidecar) "import-${fileTimestamp()}.sha256" else "import-${fileTimestamp()}.db"
        val target = File(stagingDir, safe)
        inputStream.use { input -> FileOutputStream(target).use { input.copyTo(it) } }
        return target
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }
    }

    private fun fileTimestamp(): String =
        java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date(wallClock()))
}
