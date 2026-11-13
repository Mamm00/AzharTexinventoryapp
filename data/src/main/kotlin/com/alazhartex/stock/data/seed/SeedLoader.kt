package com.alazhartex.stock.data.seed

import android.content.Context
import com.alazhartex.stock.data.local.AccountEntity
import com.alazhartex.stock.data.local.AppDatabase
import com.alazhartex.stock.data.local.AppSettingEntity
import com.alazhartex.stock.data.local.ColourEntity
import com.alazhartex.stock.data.local.FabricEntity
import com.alazhartex.stock.data.local.InventoryEntity
import com.alazhartex.stock.data.local.MappingEntity
import com.alazhartex.stock.data.local.MovementEntity
import com.alazhartex.stock.data.local.RollEntity
import com.alazhartex.stock.data.local.ShedAssignmentEntity
import com.alazhartex.stock.data.local.WarehouseEntity
import com.alazhartex.stock.data.repo.TransactionRunner
import com.alazhartex.stock.domain.idempotency.MutationOutcome
import java.time.Instant
import org.json.JSONObject

/**
 * Applies the committed seed (seed-alazhartex.json, copied into app assets at build time) on first
 * launch. Idempotent via the seed-apply-v1 idempotency key + atomic Room transaction (§4.7/§9.6).
 * No numbers are typed here — every row comes from the seed artifact verified by the oracles.
 *
 * v1.2.0: fresh installs are BLANK — sign-in accounts and app-settings defaults are always
 * applied, but the business dataset (warehouses, fabrics, colours, mappings, inventory,
 * movements, rolls, shed assignments) is NOT seeded unless the debug flag
 * [KEY_DEBUG_FULL_SEED] is set in [SEED_PREFS] (used by instrumentation tests and the debug
 * danger-zone "reload demo data" action, which wipes operational data first). The pinned seed
 * artifact + sha256 stay untouched; existing installs keep their data (opt-in wipe only).
 */
class SeedLoader(
    private val context: Context,
    db: () -> AppDatabase,
    private val wallClock: () -> Long,
) {
    private val dbSupplier: () -> AppDatabase = db
    private val database get() = dbSupplier()
    private val runner = TransactionRunner(db) { wallClock() }
    private val prefs = context.getSharedPreferences(SEED_PREFS, Context.MODE_PRIVATE)

    suspend fun seedIfNeeded(): MutationOutcome {
        if (database.appSettingsDao().get("seed_applied") == "true") {
            return MutationOutcome.AlreadyApplied
        }
        val json = readSeedJson()
        val full = prefs.getBoolean(KEY_DEBUG_FULL_SEED, false)
        return runner.withAudit("system", "seed.apply", SEED_KEY, if (full) "seed:full" else "seed:blank") { _ ->
            val root = JSONObject(json)
            applyCommon(root)
            if (full) {
                applyBusiness(root)
            }
            database.appSettingsDao().upsert(AppSettingEntity("seed_applied", "true"))
            database.appSettingsDao().upsert(AppSettingEntity("app_version", APP_VERSION))
            MutationOutcome.Applied()
        }
    }

    /**
     * Debug danger-zone action: re-applies ONLY the business dataset (warehouses .. movements +
     * seed settings). Callers must wipe operational tables first (DebugRepository does), otherwise
     * the insert conflicts abort the transaction. Accounts and markers are left untouched.
     */
    suspend fun applyDemoBusinessSeed(): MutationOutcome {
        val json = readSeedJson()
        return runner.withAudit("system", "debug.demo_reload", java.util.UUID.randomUUID().toString(), "seed:business") { _ ->
            applyBusiness(JSONObject(json))
            MutationOutcome.Applied()
        }
    }

    private fun readSeedJson(): String =
        context.assets.open("seed-alazhartex.json").bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Accounts + app-settings defaults — always applied, even on blank fresh installs. */
    private suspend fun applyCommon(root: JSONObject) {
        applyAccounts(root)
        applySettings(root)
    }

    /**
     * Warehouses, assignments, fabrics, colours, mappings, rolls, inventory, movements.
     * Masters (fabrics/colours) are inserted with IGNORE so a demo reload over an existing,
     * user-curated catalogue skips what is already there instead of aborting on the UNIQUE
     * pair (name_ar, name_en) / keeper_code. On a fresh install (empty tables) IGNORE == ABORT.
     */
    private suspend fun applyBusiness(root: JSONObject) {

        val warehouses = root.getJSONArray("warehouses").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WarehouseEntity(o.getString("id"), o.getString("name_ar"), o.getString("name_en"), o.getLong("capacity_rolls"), o.getString("keeper_account_id"))
            }
        }
        database.warehouseDao().insertAll(warehouses)

        val assignments = root.getJSONArray("shed_assignments").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ShedAssignmentEntity(o.getString("account_id"), o.getString("warehouse_id"))
            }
        }
        database.shedAssignmentDao().insertAll(assignments)

        val fabrics = root.getJSONArray("fabrics").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FabricEntity(o.getString("id"), o.getString("name_ar"), o.getString("name_en"), o.getString("family"))
            }
        }
        database.fabricDao().insertAllIgnore(fabrics)

        val colours = root.getJSONArray("colours").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ColourEntity(
                    id = o.getString("id"),
                    code = o.getString("code"),
                    nameAr = o.getString("name_ar"),
                    nameEn = o.getString("name_en"),
                    grade = o.getString("grade"),
                    family = o.getString("family"),
                    keeperCode = if (o.isNull("keeper_code")) null else o.getString("keeper_code"),
                    isActive = o.getBoolean("is_active"),
                )
            }
        }
        database.colourDao().insertAllIgnore(colours)

        val mappings = root.getJSONArray("mappings").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MappingEntity(o.getString("id"), o.getString("warehouse_id"), o.getString("fabric_id"), o.getString("colour_id"), o.getBoolean("is_active"))
            }
        }
        database.mappingDao().insertAll(mappings)

        val rolls = root.getJSONArray("rolls").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RollEntity(o.getString("id"), o.getString("mapping_id"), o.getString("state"), o.getLong("count"))
            }
        }
        database.rollDao().insertAll(rolls)

        val inventory = root.getJSONArray("inventory").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                InventoryEntity(o.getString("id"), o.getString("mapping_id"), o.getLong("closing"), o.getLong("opening_cache"), null)
            }
        }
        database.inventoryDao().insertAll(inventory)

        val movements = root.getJSONArray("movements").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MovementEntity(
                    id = o.getString("id"),
                    type = o.getString("type"),
                    mappingId = o.getString("mapping_id"),
                    qtyDelta = o.getLong("qty_delta"),
                    transferPairId = if (o.isNull("transfer_pair_id")) null else o.getString("transfer_pair_id"),
                    idempotencyKey = o.getString("idempotency_key"),
                    actorAccountId = o.getString("actor_account_id"),
                    at = Instant.parse(o.getString("at")).toEpochMilli(),
                    note = o.optString("note", ""),
                )
            }
        }
        database.movementDao().insertAll(movements)

    }

    private suspend fun applySettings(root: JSONObject) {
        val settings = root.getJSONArray("settings").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AppSettingEntity(o.getString("key"), o.getString("value"))
            }
        }
        for (setting in settings) {
            database.appSettingsDao().upsert(setting)
        }
    }

    private suspend fun applyAccounts(root: JSONObject) {
        val accounts = root.getJSONArray("accounts").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AccountEntity(o.getString("id"), o.getString("username"), o.getString("password_pbkdf2_sha256"), o.getString("role"), o.getString("display_name"))
            }
        }
        database.accountDao().insertAll(accounts)
    }

    companion object {
        const val SEED_KEY = "seed-apply-v1"
        const val APP_VERSION = "1.2.0"
        const val SEED_PREFS = "alazhartex.debug"
        const val KEY_DEBUG_FULL_SEED = "debug_full_seed_v1"
    }
}
