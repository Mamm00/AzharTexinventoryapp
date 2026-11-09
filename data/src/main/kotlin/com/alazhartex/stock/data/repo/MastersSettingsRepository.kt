package com.alazhartex.stock.data.repo

import com.alazhartex.stock.data.local.AppDatabase
import com.alazhartex.stock.data.local.AppSettingEntity
import com.alazhartex.stock.data.local.MappingEntity
import com.alazhartex.stock.domain.idempotency.MutationOutcome
import com.alazhartex.stock.domain.model.Role
import com.alazhartex.stock.domain.model.RbacDeniedException
import com.alazhartex.stock.domain.rbac.Rbac
import java.util.UUID

/**
 * Masters (warehouses/fabrics/colours/mappings) and Settings mutations.
 * ADMIN: full CRUD + Settings. KEEPER: masters ADD-ONLY. SALES_MANAGER: read-only.
 * Every write audited via TransactionRunner (spec §4.4/§4.7). Keepers never delete or regrade shared data.
 */
class MastersSettingsRepository(
    private val database: AppDatabase,
    private val wallClock: () -> Long,
) {
    private val runner = TransactionRunner(database) { wallClock() }

    private suspend fun actorRole(actorId: String): Role =
        database.accountDao().getById(actorId)?.role?.let { Role.valueOf(it) }
            ?: throw RbacDeniedException(Role.SALES_MANAGER, "unknown", actorId, "rbac.deny.unknown_actor")

    private fun verdictOrThrow(role: Role, verdict: Rbac.Verdict, action: String, target: String) {
        if (!verdict.allowed) throw RbacDeniedException(role, action, target, verdict.reasonKey ?: "rbac.deny.generic")
    }

    // ---------- masters: add (ADMIN + KEEPER) ----------
    suspend fun addColour(actorId: String, code: String, nameEn: String, nameAr: String, grade: String, family: String, keeperCode: String?, idempotencyKey: String): MutationOutcome {
        require(grade in setOf("A", "B", "C", "D")) { "grade must be A, B, C or D" }
        return runner.withAudit(actorId, Rbac.ACTION_MASTERS_ADD, idempotencyKey, "colour:$code") { _ ->
            val role = actorRole(actorId)
            verdictOrThrow(role, Rbac.canMastersAdd(role), Rbac.ACTION_MASTERS_ADD, code)
            database.colourDao().insert(
                com.alazhartex.stock.data.local.ColourEntity(
                    id = UUID.randomUUID().toString(), code = code, nameEn = nameEn, nameAr = nameAr,
                    grade = grade, family = family, keeperCode = keeperCode, isActive = true,
                ),
            )
            MutationOutcome.Applied()
        }
    }

    suspend fun addFabric(actorId: String, nameEn: String, nameAr: String, family: String, idempotencyKey: String): MutationOutcome {
        return runner.withAudit(actorId, Rbac.ACTION_MASTERS_ADD, idempotencyKey, "fabric:$nameEn") { _ ->
            val role = actorRole(actorId)
            verdictOrThrow(role, Rbac.canMastersAdd(role), Rbac.ACTION_MASTERS_ADD, nameEn)
            database.fabricDao().insert(
                com.alazhartex.stock.data.local.FabricEntity(UUID.randomUUID().toString(), nameEn = nameEn, nameAr = nameAr, family = family),
            )
            MutationOutcome.Applied()
        }
    }

    suspend fun addWarehouse(actorId: String, id: String, nameEn: String, nameAr: String, capacityRolls: Long, keeperAccountId: String, idempotencyKey: String): MutationOutcome {
        require(capacityRolls >= 0) { "capacity must be >= 0" }
        return runner.withAudit(actorId, Rbac.ACTION_MASTERS_ADD, idempotencyKey, "warehouse:$id") { _ ->
            val role = actorRole(actorId)
            if (role != Role.ADMIN) {
                throw RbacDeniedException(role, Rbac.ACTION_MASTERS_ADD, id, "rbac.deny.keeper_warehouse_provision")
            }
            database.warehouseDao().insert(
                com.alazhartex.stock.data.local.WarehouseEntity(id, nameAr, nameEn, capacityRolls, keeperAccountId),
            )
            MutationOutcome.Applied()
        }
    }

    /** Add mapping (+ its zero inventory row + zero ACTIVE roll row) — one transaction. */
    suspend fun addMapping(actorId: String, warehouseId: String, fabricId: String, colourId: String, idempotencyKey: String): MutationOutcome {
        return runner.withAudit(actorId, Rbac.ACTION_MASTERS_ADD, idempotencyKey, "mapping:$warehouseId|$fabricId|$colourId") { _ ->
            val role = actorRole(actorId)
            verdictOrThrow(role, Rbac.canMastersAdd(role), Rbac.ACTION_MASTERS_ADD, "$warehouseId|$fabricId|$colourId")
            val mappingId = UUID.randomUUID().toString()
            database.mappingDao().insert(MappingEntity(mappingId, warehouseId, fabricId, colourId, true))
            database.inventoryDao().insertAll(
                listOf(com.alazhartex.stock.data.local.InventoryEntity(UUID.randomUUID().toString(), mappingId, 0L, 0L, null)),
            )
            MutationOutcome.Applied()
        }
    }

    // ---------- masters: delete / regrade (ADMIN only) ----------
    suspend fun regradeColour(actorId: String, colourId: String, newGrade: String, idempotencyKey: String): MutationOutcome {
        require(newGrade in setOf("A", "B", "C", "D")) { "grade must be A, B, C or D" }
        return runner.withAudit(actorId, Rbac.ACTION_REGRADE, idempotencyKey, "colour:$colourId") { _ ->
            val role = actorRole(actorId)
            verdictOrThrow(role, Rbac.canMastersDeleteOrRegrade(role), Rbac.ACTION_REGRADE, colourId)
            database.colourDao().updateGrade(colourId, newGrade)
            MutationOutcome.Applied()
        }
    }

    suspend fun deleteFabric(actorId: String, fabricId: String, idempotencyKey: String): MutationOutcome {
        return runner.withAudit(actorId, Rbac.ACTION_MASTERS_DELETE, idempotencyKey, "fabric:$fabricId") { _ ->
            val role = actorRole(actorId)
            verdictOrThrow(role, Rbac.canMastersDeleteOrRegrade(role), Rbac.ACTION_MASTERS_DELETE, fabricId)
            if (database.fabricDao().mappingCountForFab(fabricId) > 0L) {
                throw com.alazhartex.stock.domain.model.InvariantViolationException("fabric_has_mappings fabric=$fabricId")
            }
            database.fabricDao().delete(fabricId)
            MutationOutcome.Applied()
        }
    }

    // ---------- settings (ADMIN only; keepers: NO settings) ----------
    suspend fun setSetting(actorId: String, key: String, value: String, idempotencyKey: String): MutationOutcome {
        return runner.withAudit(actorId, Rbac.ACTION_SETTINGS, idempotencyKey, "setting:$key") { _ ->
            val role = actorRole(actorId)
            verdictOrThrow(role, Rbac.canSettings(role), Rbac.ACTION_SETTINGS, key)
            database.appSettingsDao().upsert(AppSettingEntity(key, value))
            MutationOutcome.Applied()
        }
    }
}
