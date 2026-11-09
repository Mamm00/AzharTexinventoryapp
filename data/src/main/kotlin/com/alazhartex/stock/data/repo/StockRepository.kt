package com.alazhartex.stock.data.repo

import com.alazhartex.stock.data.local.AppDatabase
import com.alazhartex.stock.data.local.MovementEntity
import com.alazhartex.stock.domain.idempotency.MutationOutcome
import com.alazhartex.stock.domain.model.InvariantViolationException
import com.alazhartex.stock.domain.model.MovementType
import com.alazhartex.stock.domain.model.RbacDeniedException
import com.alazhartex.stock.domain.model.ShortfallException
import com.alazhartex.stock.domain.rbac.Rbac
import com.alazhartex.stock.domain.stock.StockMath
import java.util.UUID

/**
 * Stock mutations (spec §4.5). Every public write goes through TransactionRunner.withAudit.
 * Read-check-write runs in ONE withTransaction block together with the write (§9.6).
 * Negative-stock refusal uses an ATOMIC conditional UPDATE, so concurrent removals lose safely.
 */
class StockRepository(
    private val database: AppDatabase,
    private val wallClock: () -> Long,
    private val sessionVerdict: suspend (actorId: String, actorIsAdmin: Boolean) -> com.alazhartex.stock.domain.session.SessionPolicy.MutationResolution,
) {
    private val runner = TransactionRunner(database) { wallClock() }

    private suspend fun requireSessionAndRole(actorId: String): Pair<String, Set<String>> {
        val account = database.accountDao().getById(actorId) ?: throw RbacDeniedException(
            com.alazhartex.stock.domain.model.Role.KEEPER, "session", actorId, "rbac.deny.unknown_actor",
        )
        val assignments = database.shedAssignmentDao().shedsFor(actorId).toSet()
        val isAdmin = account.role == "ADMIN"
        when (sessionVerdict(actorId, isAdmin)) {
            com.alazhartex.stock.domain.session.SessionPolicy.MutationResolution.PROCEED -> Unit
            com.alazhartex.stock.domain.session.SessionPolicy.MutationResolution.REFUSE_EXPIRED ->
                throw RbacDeniedException(com.alazhartex.stock.domain.model.Role.KEEPER, "session", actorId, "session.expired")
            com.alazhartex.stock.domain.session.SessionPolicy.MutationResolution.REFUSE_ANOMALY ->
                throw com.alazhartex.stock.domain.model.SessionClockAnomalyException("SESSION_CLOCK_ANOMALY")
            com.alazhartex.stock.domain.session.SessionPolicy.MutationResolution.SILENT_REISSUE_AND_PROCEED -> Unit // admin: refresh handled inside verdict
        }
        return account.role to assignments
    }

    private suspend fun requireWriteScope(actorRole: String, targetShed: String, assignments: Set<String>) {
        val role = com.alazhartex.stock.domain.model.Role.valueOf(actorRole)
        val verdict = Rbac.canWriteStock(role, targetShed, assignments)
        if (!verdict.allowed) {
            throw RbacDeniedException(role, "write:$targetShed", targetShed, verdict.reasonKey ?: "rbac.deny.foreign_shed")
        }
    }

    private suspend fun recheckRow(mappingId: String) {
        val row = database.inventoryDao().getByMapping(mappingId)
            ?: throw InvariantViolationException("inventory_missing mapping=$mappingId")
        val deltas = database.movementDao().sumDeltaFor(mappingId)
        val opening = StockMath.derivedOpening(row.closing, deltas)
        StockMath.requireNonNegative(row.closing)
        database.inventoryDao().updateOpeningCache(mappingId, opening, wallClock())
        StockMath.checkInvariant(mappingId, opening, row.closing, deltas)
    }

    /** IN / ADJUSTMENT-positive path (increases stock). */
    suspend fun addRolls(actorId: String, mappingId: String, qty: Long, type: MovementType, note: String, idempotencyKey: String): MutationOutcome {
        require(qty > 0) { "qty must be positive" }
        require(type == MovementType.IN || type == MovementType.ADJUSTMENT) { "use addRolls only for IN/ADJUSTMENT(+)" }
        return runner.withAudit(actorId, "stock.${type.name.lowercase()}", idempotencyKey, mappingId) { ctx ->
            val (role, assignments) = requireSessionAndRole(actorId)
            val mapping = requireMapping(mappingId)
            requireWriteScope(role, mapping.warehouseId, assignments)
            if (isReplay(idempotencyKey)) return@withAudit MutationOutcome.AlreadyApplied
            database.inventoryDao().increment(mappingId, qty, wallClock())
            applyRollDelta(mappingId, qty)
            database.movementDao().insert(movement(ctx, mappingId, type, qty, null, note))
            recheckRow(mappingId)
            MutationOutcome.Applied()
        }
    }

    /** OUT / ADJUSTMENT-negative (decreases stock) — atomic conditional update. */
    suspend fun removeRolls(actorId: String, mappingId: String, qty: Long, type: MovementType, note: String, idempotencyKey: String): MutationOutcome {
        require(qty > 0) { "qty must be positive" }
        require(type == MovementType.OUT || type == MovementType.ADJUSTMENT) { "use removeRolls only for OUT/ADJUSTMENT(-)" }
        return runner.withAudit(actorId, "stock.${type.name.lowercase()}", idempotencyKey, mappingId) { ctx ->
            val (role, assignments) = requireSessionAndRole(actorId)
            val mapping = requireMapping(mappingId)
            requireWriteScope(role, mapping.warehouseId, assignments)
            if (isReplay(idempotencyKey)) return@withAudit MutationOutcome.AlreadyApplied
            val now = wallClock()
            val updated = database.inventoryDao().conditionalDecrement(mappingId, qty, now)
            if (updated == 0) {
                val available = database.inventoryDao().getByMapping(mappingId)?.closing ?: 0L
                throw ShortfallException(mappingId, qty, available)
            }
            applyRollDelta(mappingId, -qty)
            database.movementDao().insert(movement(ctx, mappingId, type, -qty, null, note))
            recheckRow(mappingId)
            MutationOutcome.Applied()
        }
    }

    /** Transfer between two mappings (spec §4.5 transfer compensation, T6, D6). */
    suspend fun transferRolls(actorId: String, sourceMappingId: String, targetMappingId: String, qty: Long, note: String, idempotencyKey: String): MutationOutcome {
        require(qty > 0) { "qty must be positive" }
        require(sourceMappingId != targetMappingId) { "source and target must differ" }
        return runner.withAudit(actorId, "stock.transfer", idempotencyKey, "$sourceMappingId->$targetMappingId") { ctx ->
            val (role, assignments) = requireSessionAndRole(actorId)
            val source = requireMapping(sourceMappingId)
            val target = requireMapping(targetMappingId)
            requireWriteScope(role, source.warehouseId, assignments)
            requireWriteScope(role, target.warehouseId, assignments)
            if (isReplay(idempotencyKey)) return@withAudit MutationOutcome.AlreadyApplied
            val now = wallClock()
            val updated = database.inventoryDao().conditionalDecrement(sourceMappingId, qty, now)
            if (updated == 0) {
                val available = database.inventoryDao().getByMapping(sourceMappingId)?.closing ?: 0L
                throw ShortfallException(sourceMappingId, qty, available)
            }
            database.inventoryDao().increment(targetMappingId, qty, now)
            val pairId = UUID.randomUUID().toString()
            database.movementDao().insert(movement(ctx, sourceMappingId, MovementType.TRANSFER_OUT, -qty, pairId, note))
            database.movementDao().insert(movement(ctx, targetMappingId, MovementType.TRANSFER_IN, qty, pairId, note))
            applyRollDelta(sourceMappingId, -qty)
            applyRollDelta(targetMappingId, qty)
            recheckRow(sourceMappingId)
            recheckRow(targetMappingId)
            MutationOutcome.Applied()
        }
    }

    private suspend fun requireMapping(mappingId: String) =
        database.mappingDao().getById(mappingId)
            ?: throw InvariantViolationException("mapping_missing mapping=$mappingId")

    private suspend fun isReplay(idempotencyKey: String): Boolean =
        database.movementDao().countByIdempotency(idempotencyKey) > 0L ||
            database.auditDao().countByIdempotency(idempotencyKey) > 0L

    private fun movement(ctx: WriteContext, mappingId: String, type: MovementType, qtyDelta: Long, transferPairId: String?, note: String) =
        MovementEntity(
            id = UUID.randomUUID().toString(),
            type = type.name,
            mappingId = mappingId,
            qtyDelta = qtyDelta,
            transferPairId = transferPairId,
            idempotencyKey = ctx.idempotencyKey,
            actorAccountId = ctx.actorId,
            at = wallClock(),
            note = note,
        )

    private suspend fun applyRollDelta(mappingId: String, qtyDelta: Long) {
        val roll = database.rollDao().getByMappingAndState(mappingId, "ACTIVE") ?: return
        val newCount = roll.count + qtyDelta
        if (newCount < 0L) {
            throw InvariantViolationException("roll_count_negative mapping=$mappingId count=$newCount")
        }
        database.rollDao().updateCount(mappingId, "ACTIVE", newCount)
    }
}
