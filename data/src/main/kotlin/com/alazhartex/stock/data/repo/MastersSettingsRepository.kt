package com.alazhartex.stock.data.repo

import com.alazhartex.stock.data.local.AppDatabase
import com.alazhartex.stock.data.local.AppSettingEntity
import com.alazhartex.stock.data.local.MappingEntity
import com.alazhartex.stock.domain.idempotency.MutationOutcome
import com.alazhartex.stock.domain.model.DuplicateValueException
import com.alazhartex.stock.domain.model.InvariantViolationException
import com.alazhartex.stock.domain.model.RbacDeniedException
import com.alazhartex.stock.domain.model.ReferencedByException
import com.alazhartex.stock.domain.model.Role
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

    // ---------- v1.1 validation helpers (delta contract §12) ----------
    private val controlChars = Regex("[\u0000-\u001F\u007F]")

    private fun requireName(value: String, field: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "$field must not be empty" }
        require(trimmed.length <= 80) { "$field must be at most 80 characters" }
        require(!controlChars.containsMatchIn(trimmed)) { "$field must not contain control characters" }
        return trimmed
    }

    private fun requireFamily(family: String): String {
        val trimmed = family.trim()
        require(trimmed.isNotEmpty()) { "family must not be empty" }
        return trimmed
    }

    suspend fun duplicateFabricCount(excludeId: String?, nameAr: String, nameEn: String): Long =
        if (excludeId == null) {
            database.fabricDao().duplicateCount(nameAr, nameEn)
        } else {
            database.fabricDao().duplicateCount(excludeId, nameAr, nameEn)
        }

    // ---------- masters: add (ADMIN + KEEPER) ----------
    suspend fun addColour(actorId: String, code: String, nameEn: String, nameAr: String, grade: String, family: String, keeperCode: String?, idempotencyKey: String): MutationOutcome {
        require(grade in setOf("A", "B", "C", "D")) { "grade must be A, B, C or D" }
        val cleanCode = code.trim().uppercase()
        val cleanEn = requireName(nameEn, "name_en")
        val cleanAr = requireName(nameAr, "name_ar")
        val cleanFamily = requireFamily(family)
        require(cleanCode.matches(Regex("[A-Z0-9-]{1,16}"))) { "code must match [A-Z0-9-]{1,16}" }
        return runner.withAudit(actorId, Rbac.ACTION_MASTERS_COLOUR_ADD, idempotencyKey, "colour:$cleanCode") { _ ->
            val role = actorRole(actorId)
            verdictOrThrow(role, Rbac.canMastersAdd(role), Rbac.ACTION_MASTERS_COLOUR_ADD, cleanCode)
            if (database.colourDao().getByCode(cleanCode) != null) {
                throw DuplicateValueException("colour_code_duplicate code=$cleanCode", "colour_duplicate_error")
            }
            // KEEPER codes are namespaced: K- auto-prefix (delta contract §12).
            val finalKeeperCode = if (role == Role.KEEPER) {
                val raw = keeperCode?.trim()?.removePrefix("K-")?.takeIf { it.isNotEmpty() } ?: cleanCode
                "K-$raw".take(16)
            } else {
                keeperCode?.trim()?.takeIf { it.isNotEmpty() }?.take(16)
            }
            database.colourDao().insert(
                com.alazhartex.stock.data.local.ColourEntity(
                    id = UUID.randomUUID().toString(), code = cleanCode, nameEn = cleanEn, nameAr = cleanAr,
                    grade = grade, family = cleanFamily, keeperCode = finalKeeperCode, isActive = true,
                ),
            )
            MutationOutcome.Applied()
        }
    }

    suspend fun addFabric(actorId: String, nameEn: String, nameAr: String, family: String, idempotencyKey: String): MutationOutcome {
        val cleanEn = requireName(nameEn, "name_en")
        val cleanAr = requireName(nameAr, "name_ar")
        val cleanFamily = requireFamily(family)
        return runner.withAudit(actorId, Rbac.ACTION_MASTERS_FABRIC_ADD, idempotencyKey, "fabric:$cleanEn") { _ ->
            val role = actorRole(actorId)
            verdictOrThrow(role, Rbac.canMastersAdd(role), Rbac.ACTION_MASTERS_FABRIC_ADD, cleanEn)
            if (duplicateFabricCount(null, cleanAr, cleanEn) > 0) {
                throw DuplicateValueException("fabric_duplicate ar=$cleanAr en=$cleanEn", "fabric_duplicate_error")
            }
            database.fabricDao().insert(
                com.alazhartex.stock.data.local.FabricEntity(UUID.randomUUID().toString(), nameEn = cleanEn, nameAr = cleanAr, family = cleanFamily),
            )
            MutationOutcome.Applied()
        }
    }

    /** v1.1: ADMIN-only fabric edit (seeded ids pinned). Names validated like add. */
    suspend fun editFabric(actorId: String, fabricId: String, nameEn: String, nameAr: String, family: String, idempotencyKey: String): MutationOutcome {
        val cleanEn = requireName(nameEn, "name_en")
        val cleanAr = requireName(nameAr, "name_ar")
        val cleanFamily = requireFamily(family)
        return runner.withAudit(actorId, Rbac.ACTION_MASTERS_FABRIC_EDIT, idempotencyKey, "fabric:$fabricId") { _ ->
            val role = actorRole(actorId)
            verdictOrThrow(role, Rbac.canMastersEdit(role), Rbac.ACTION_MASTERS_FABRIC_EDIT, fabricId)
            if (duplicateFabricCount(fabricId, cleanAr, cleanEn) > 0) {
                throw DuplicateValueException("fabric_duplicate ar=$cleanAr en=$cleanEn", "fabric_duplicate_error")
            }
            database.fabricDao().update(fabricId, cleanAr, cleanEn, cleanFamily)
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
                throw ReferencedByException("fabric_has_mappings fabric=$fabricId", "referenced_by_mappings")
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
    // ---------- v1.1 Fabrics Edit operations (delta contract §12) ----------

    /** Edit colour: code is IMMUTABLE (seeded ids pinned); grade/family/active/names mutable by ADMIN. */
    suspend fun editColour(actorId: String, colourId: String, nameEn: String, nameAr: String, grade: String, family: String, isActive: Boolean, idempotencyKey: String): MutationOutcome {
        require(grade in setOf("A", "B", "C", "D")) { "grade must be A, B, C or D" }
        val cleanEn = requireName(nameEn, "name_en")
        val cleanAr = requireName(nameAr, "name_ar")
        val cleanFamily = requireFamily(family)
        return runner.withAudit(actorId, Rbac.ACTION_MASTERS_COLOUR_EDIT, idempotencyKey, "colour:$colourId") { _ ->
            val role = actorRole(actorId)
            verdictOrThrow(role, Rbac.canMastersEdit(role), Rbac.ACTION_MASTERS_COLOUR_EDIT, colourId)
            database.colourDao().updateDetails(colourId, cleanAr, cleanEn, grade, cleanFamily, isActive)
            MutationOutcome.Applied()
        }
    }

    /** Delete colour: ADMIN only; refused while ANY mapping, roll or movement references it. */
    suspend fun deleteColour(actorId: String, colourId: String, idempotencyKey: String): MutationOutcome {
        return runner.withAudit(actorId, Rbac.ACTION_MASTERS_COLOUR_DELETE, idempotencyKey, "colour:$colourId") { _ ->
            val role = actorRole(actorId)
            verdictOrThrow(role, Rbac.canMastersEdit(role), Rbac.ACTION_MASTERS_COLOUR_DELETE, colourId)
            val mappings = database.colourDao().mappingCountForColour(colourId)
            if (mappings > 0L) {
                throw ReferencedByException("colour_has_mappings colour=$colourId mappings=$mappings", "referenced_by_mappings")
            }
            val movements = database.movementDao().countForColour(colourId)
            if (movements > 0L) {
                throw ReferencedByException("colour_has_movements colour=$colourId movements=$movements", "referenced_by_movements")
            }
            val activeRolls = database.rollDao().sumForColour(colourId, "ACTIVE")
            val inactiveRolls = database.rollDao().sumForColour(colourId, "INACTIVE")
            if (activeRolls > 0L || inactiveRolls > 0L) {
                throw ReferencedByException("colour_has_rolls colour=$colourId rolls=${activeRolls + inactiveRolls}", "referenced_by_rolls")
            }
            database.colourDao().delete(colourId)
            MutationOutcome.Applied()
        }
    }

    /**
     * Bulk "Assign to fabric": create one mapping per shed x selected colours, each with a
     * zeroed inventory row and a zeroed INACTIVE roll row. ONE transaction, ONE idempotency
     * key, ONE audit row (masters.mapping.bulk_assign). RBAC inherits masters ADD (ADMIN + KEEPER).
     * Pairs that already have a mapping are skipped, so the fan-out is idempotent by pair.
     */
    suspend fun bulkAssignMapping(actorId: String, fabricId: String, colourIds: List<String>, idempotencyKey: String): Int {
        require(colourIds.isNotEmpty()) { "select at least one colour" }
        var createdCount = 0
        runner.withAudit(actorId, Rbac.ACTION_MASTERS_BULK_ASSIGN, idempotencyKey, "fabric:$fabricId colours:${colourIds.size}") { _ ->
            val role = actorRole(actorId)
            verdictOrThrow(role, Rbac.canBulkAssign(role), Rbac.ACTION_MASTERS_BULK_ASSIGN, fabricId)
            if (database.fabricDao().getById(fabricId) == null) {
                throw InvariantViolationException("fabric_missing fabric=$fabricId")
            }
            val warehouses = database.warehouseDao().getAll()
            val created = mutableListOf<com.alazhartex.stock.data.local.MappingEntity>()
            for (warehouse in warehouses) {
                for (colourId in colourIds) {
                    if (database.mappingDao().findIds(warehouse.id, fabricId, colourId).isNotEmpty()) continue
                    created += com.alazhartex.stock.data.local.MappingEntity(
                        id = UUID.randomUUID().toString(),
                        warehouseId = warehouse.id,
                        fabricId = fabricId,
                        colourId = colourId,
                        isActive = true,
                    )
                }
            }
            if (created.isEmpty()) {
                throw InvariantViolationException("bulk_assign_nothing_to_create")
            }
            database.mappingDao().insertAll(created)
            database.inventoryDao().insertAll(
                created.map { com.alazhartex.stock.data.local.InventoryEntity(UUID.randomUUID().toString(), it.id, 0L, 0L, null) },
            )
            database.rollDao().insertAll(
                created.map { com.alazhartex.stock.data.local.RollEntity(UUID.randomUUID().toString(), it.id, "INACTIVE", 0L) },
            )
            createdCount = created.size
            MutationOutcome.Applied()
        }
        return createdCount
    }
}
