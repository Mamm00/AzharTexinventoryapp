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
import org.json.JSONObject
import java.time.Instant

/**
 * Applies the committed seed (seed-alazhartex.json, copied into app assets at build time) on first
 * launch. Idempotent via the seed-apply-v1 idempotency key + atomic Room transaction (§4.7/§9.6).
 * No numbers are typed here — every row comes from the seed artifact verified by the oracles.
 */
class SeedLoader(
    private val context: Context,
    private val database: AppDatabase,
    private val wallClock: () -> Long,
) {
    private val runner = TransactionRunner(database) { wallClock() }
    suspend fun seedIfNeeded(): MutationOutcome {
        if (database.appSettingsDao().get("seed_applied") == "true") {
            return MutationOutcome.AlreadyApplied
        }
        val json = context.assets.open("seed-alazhartex.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return runner.withAudit("system", "seed.apply", SEED_KEY, "seed") { _ ->
            applySeed(JSONObject(json))
            database.appSettingsDao().upsert(AppSettingEntity("seed_applied", "true"))
            database.appSettingsDao().upsert(AppSettingEntity("app_version", APP_VERSION))
            MutationOutcome.Applied()
        }
    }

    private suspend fun applySeed(root: JSONObject) {
        val accounts = root.getJSONArray("accounts").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AccountEntity(o.getString("id"), o.getString("username"), o.getString("password_pbkdf2_sha256"), o.getString("role"), o.getString("display_name"))
            }
        }
        database.accountDao().insertAll(accounts)

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
        database.fabricDao().insertAll(fabrics)

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
        database.colourDao().insertAll(colours)

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

    companion object {
        const val SEED_KEY = "seed-apply-v1"
        const val APP_VERSION = "1.0.0"
    }
}
