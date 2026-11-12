package com.alazhartex.stock.data.repo

import android.content.Context
import com.alazhartex.stock.data.local.AppDatabase
import com.alazhartex.stock.data.local.WarehouseEntity
import com.alazhartex.stock.data.seed.SeedLoader
import com.alazhartex.stock.domain.idempotency.MutationOutcome
import com.alazhartex.stock.domain.model.DuplicateValueException
import com.alazhartex.stock.domain.model.ReferencedByException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * v1.2.0 — debug danger zone. FULL CONTROL, NO RBAC, for ANY signed-in actor.
 *
 * Every mutation is audited through TransactionRunner (ALLOWED/BLOCKED) like every other write in
 * the app. Destructive operations take a mandatory VACUUM INTO safety backup into
 * files/backups/ (keep last 3 of the same prefix) and log pruning to files/backups/prune.log —
 * never silently drop the user's data without a copy, even though access is unrestricted.
 *
 * wipeOperationalData() deletes warehouses, shed assignments, mappings, inventory, movements,
 * rolls and clears the export manifest — but KEEPS the fabrics/colours masters catalogue, the
 * accounts and all settings. reloadDemoData() = wipe + re-apply the pinned demo business seed.
 */
class DebugRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val wallClock: () -> Long,
    private val seedLoader: SeedLoader,
) {
    private val runner = TransactionRunner(database) { wallClock() }

    data class WarehouseCascadeCounts(
        val mappings: Long,
        val inventoryRows: Long,
        val movements: Long,
        val rolls: Long,
        val assignments: Long,
    )

    data class OperationalCounts(
        val warehouses: Long,
        val assignments: Long,
        val mappings: Long,
        val inventoryRows: Long,
        val movements: Long,
        val rolls: Long,
        val fabrics: Long,
        val colours: Long,
    )

    data class WarehouseSummary(
        val id: String,
        val nameAr: String,
        val nameEn: String,
        val capacityRolls: Long,
        val keeperAccountId: String,
        val keeperUsername: String,
        val closing: Long,
        val activeRolls: Long,
    )

    /** Non-restricted sanity: names trimmed, ≤80 chars, no control chars. */
    private fun cleanName(value: String, field: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "$field must not be empty" }
        require(trimmed.length <= 80) { "$field must be at most 80 characters" }
        require(!Regex("[\\u0000-\\u001F\\u007F]").containsMatchIn(trimmed)) { "$field must not contain control characters" }
        return trimmed
    }

    private fun cleanWarehouseId(value: String): String {
        val trimmed = value.trim().lowercase(Locale.US)
        require(trimmed.matches(Regex("^[a-z0-9][a-z0-9_-]{0,31}$"))) { "warehouse id must match ^[a-z0-9][a-z0-9_-]{0,31}\$" }
        return trimmed
    }

    private fun cleanCapacity(value: Long): Long {
        require(value >= 0L) { "capacity must be >= 0" }
        return value
    }

    /**
     * Mandatory pre-destruction safety copy (never inside the Room DB). Keep last 3 per prefix.
     * Uses a short-lived android.database.sqlite connection to the Room database file rather than
     * Room internals, so it is independent of the Room version; the main connection is idle at
     * this point (no mutation transaction is open) and VACUUM INTO produces a consistent copy.
     */
    private fun safetyBackup(prefix: String): File {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(wallClock()))
        val out = File(dir, "safety-$prefix-$ts.db")
        if (out.exists()) out.delete()
        val escaped = out.absolutePath.replace("'", "''")
        val dbPath = context.getDatabasePath("alazhartex.db").absolutePath
        var attempt = 0
        while (true) {
            try {
                val conn = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbPath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
                )
                try {
                    // PRAGMA wal_checkpoint RETURNS a row, so it cannot go through execSQL
                    // ("Queries can be performed using SQLiteDatabase query or rawQuery methods only").
                    conn.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                        while (cursor.moveToNext()) { /* drain the result row */ }
                    }
                    conn.execSQL("VACUUM INTO '$escaped'") // returns no rows — execSQL is legal here
                } finally {
                    conn.close()
                }
                break
            } catch (e: android.database.sqlite.SQLiteDatabaseLockedException) {
                if (++attempt >= 3) throw e
                Thread.sleep(150)
            }
        }
        // prune: keep newest 3 safety-*.db of this prefix
        val samePrefix = dir.listFiles { f -> f.isFile && f.name.startsWith("safety-$prefix-") && f.name.endsWith(".db") }
            ?.sortedByDescending { it.name }
            .orEmpty()
        val pruned = samePrefix.drop(3)
        for (f in pruned) {
            if (f.delete()) {
                File(dir, "prune.log").appendText("pruned ${f.name} at ${System.currentTimeMillis()}\n", Charsets.UTF_8)
            }
        }
        return out
    }

    // ---------- reads ----------

    suspend fun accounts(): List<com.alazhartex.stock.data.local.AccountEntity> =
        database.accountDao().getAll()

    suspend fun warehouseSummaries(): List<WarehouseSummary> {
        val keeperNames = database.accountDao().getAll().associateBy { it.id }
        return database.warehouseDao().getAll().map { w ->
            WarehouseSummary(
                id = w.id,
                nameAr = w.nameAr,
                nameEn = w.nameEn,
                capacityRolls = w.capacityRolls,
                keeperAccountId = w.keeperAccountId,
                keeperUsername = keeperNames[w.keeperAccountId]?.username ?: w.keeperAccountId,
                closing = database.inventoryDao().sumClosingForShed(w.id),
                activeRolls = database.rollDao().sumActiveForShed(w.id),
            )
        }
    }

    suspend fun warehouseCascadeCounts(warehouseId: String): WarehouseCascadeCounts =
        WarehouseCascadeCounts(
            mappings = database.mappingDao().countForWarehouse(warehouseId),
            inventoryRows = database.inventoryDao().countForWarehouse(warehouseId),
            movements = database.movementDao().countForWarehouse(warehouseId),
            rolls = database.rollDao().countForWarehouse(warehouseId),
            assignments = database.shedAssignmentDao().countForWarehouse(warehouseId),
        )

    suspend fun operationalCounts(): OperationalCounts =
        OperationalCounts(
            warehouses = database.warehouseDao().countAll(),
            assignments = database.shedAssignmentDao().countAll(),
            mappings = database.mappingDao().countAll(),
            inventoryRows = database.inventoryDao().countAll(),
            movements = database.movementDao().countAll(),
            rolls = database.rollDao().countAll(),
            fabrics = database.fabricDao().getAll().size.toLong(),
            colours = database.colourDao().getAll().size.toLong(),
        )

    // ---------- warehouse CRUD (unrestricted) ----------

    suspend fun addWarehouse(actorId: String, id: String, nameAr: String, nameEn: String, capacityRolls: Long, keeperAccountId: String): MutationOutcome {
        val cleanId = cleanWarehouseId(id)
        val cleanAr = cleanName(nameAr, "name_ar")
        val cleanEn = cleanName(nameEn, "name_en")
        val cleanCapacity = cleanCapacity(capacityRolls)
        val cleanKeeper = keeperAccountId.trim()
        return runner.withAudit(actorId, "debug.warehouse.add", UUID.randomUUID().toString(), "warehouse:$cleanId") { _ ->
            if (database.accountDao().getById(cleanKeeper) == null) {
                throw ReferencedByException("keeper_account:$cleanKeeper", "keeper_account_not_found")
            }
            if (database.warehouseDao().getById(cleanId) != null) {
                throw DuplicateValueException("warehouse_duplicate id=$cleanId", "warehouse_id_duplicate")
            }
            database.warehouseDao().insert(WarehouseEntity(cleanId, cleanAr, cleanEn, cleanCapacity, cleanKeeper))
            // Keep the assignment table consistent: keeper -> warehouse.
            database.shedAssignmentDao().insertAll(listOf(com.alazhartex.stock.data.local.ShedAssignmentEntity(cleanKeeper, cleanId)))
            MutationOutcome.Applied()
        }
    }

    suspend fun updateWarehouse(actorId: String, id: String, nameAr: String, nameEn: String, capacityRolls: Long, keeperAccountId: String): MutationOutcome {
        val cleanId = id.trim().lowercase(Locale.US)
        val cleanAr = cleanName(nameAr, "name_ar")
        val cleanEn = cleanName(nameEn, "name_en")
        val cleanCapacity = cleanCapacity(capacityRolls)
        val cleanKeeper = keeperAccountId.trim()
        return runner.withAudit(actorId, "debug.warehouse.edit", UUID.randomUUID().toString(), "warehouse:$cleanId") { _ ->
            if (database.warehouseDao().getById(cleanId) == null) {
                throw ReferencedByException("warehouse:$cleanId", "warehouse_not_found")
            }
            if (database.accountDao().getById(cleanKeeper) == null) {
                throw ReferencedByException("keeper_account:$cleanKeeper", "keeper_account_not_found")
            }
            val existing = database.warehouseDao().getById(cleanId)!!
            database.warehouseDao().updateDetails(cleanId, cleanAr, cleanEn, cleanCapacity, cleanKeeper)
            // Re-point the keeper assignment only when the keeper actually changed.
            if (existing.keeperAccountId != cleanKeeper) {
                database.shedAssignmentDao().deleteForWarehouse(cleanId)
                database.shedAssignmentDao().insertAll(listOf(com.alazhartex.stock.data.local.ShedAssignmentEntity(cleanKeeper, cleanId)))
            }
            MutationOutcome.Applied()
        }
    }

    suspend fun deleteWarehouse(actorId: String, warehouseId: String): MutationOutcome {
        val cleanId = warehouseId.trim().lowercase(Locale.US)
        safetyBackup("warehouse")
        val planned = warehouseCascadeCounts(cleanId)
        val target = "warehouse:$cleanId map=${planned.mappings} inv=${planned.inventoryRows} mov=${planned.movements} rolls=${planned.rolls} asg=${planned.assignments}"
        return runner.withAudit(actorId, "debug.warehouse.delete", UUID.randomUUID().toString(), target) { _ ->
            if (database.warehouseDao().getById(cleanId) == null) {
                throw ReferencedByException("warehouse:$cleanId", "warehouse_not_found")
            }
            database.movementDao().deleteForWarehouse(cleanId)
            database.rollDao().deleteForWarehouse(cleanId)
            database.inventoryDao().deleteForWarehouse(cleanId)
            database.mappingDao().deleteForWarehouse(cleanId)
            database.shedAssignmentDao().deleteForWarehouse(cleanId)
            database.warehouseDao().delete(cleanId)
            MutationOutcome.Applied()
        }
    }

    // ---------- destructive full-control wipes ----------

    /** Deletes ALL warehouses + all operational data. Keeps fabrics/colours masters, accounts, settings, audit log. */
    suspend fun wipeOperationalData(actorId: String): MutationOutcome {
        safetyBackup("wipe")
        val planned = operationalCounts()
        val target = "wipe:operational wh=${planned.warehouses} asg=${planned.assignments} map=${planned.mappings} inv=${planned.inventoryRows} mov=${planned.movements} rolls=${planned.rolls} masters=keep"
        return runner.withAudit(actorId, "debug.wipe_operational", UUID.randomUUID().toString(), target) { _ ->
            database.movementDao().deleteAll()
            database.rollDao().deleteAll()
            database.inventoryDao().deleteAll()
            database.mappingDao().deleteAll()
            database.shedAssignmentDao().deleteAll()
            database.warehouseDao().deleteAll()
            database.manifestDao().clear()
            MutationOutcome.Applied()
        }
    }

    /** Wipe operational data, then re-apply the pinned demo business seed (fresh blank look with demo data back). */
    suspend fun reloadDemoData(actorId: String): MutationOutcome {
        wipeOperationalData(actorId)
        return seedLoader.applyDemoBusinessSeed()
    }
}
