package com.alazhartex.stock.data.repo

import androidx.room.withTransaction
import com.alazhartex.stock.data.local.AppDatabase
import com.alazhartex.stock.data.local.AuditLogEntity
import com.alazhartex.stock.domain.idempotency.MutationOutcome
import com.alazhartex.stock.domain.model.DuplicateValueException
import com.alazhartex.stock.domain.model.InvariantViolationException
import com.alazhartex.stock.domain.model.RbacDeniedException
import com.alazhartex.stock.domain.model.ReferencedByException
import com.alazhartex.stock.domain.model.ShortfallException
import java.util.UUID

/**
 * THE single entry point for every write (spec §4.7, §9.6).
 * Opens ONE Room transaction, applies the mutation via [block], re-checks the affected rows'
 * invariant inside the same transaction, writes exactly one ALLOWED audit row, commits.
 * Any exception rolls everything back and writes exactly one BLOCKED audit row (reason populated).
 */
class TransactionRunner(
    private val database: AppDatabase,
    private val wallClock: () -> Long,
) {
    suspend fun <R : MutationOutcome> withAudit(
        actorId: String,
        action: String,
        idempotencyKey: String,
        target: String,
        block: suspend (WriteContext) -> R,
    ): R {
        return try {
            database.withTransaction {
                val ctx = WriteContext(actorId = actorId, idempotencyKey = idempotencyKey, action = action, wallMillis = wallClock())
                val outcome = block(ctx)
                when (outcome) {
                    is MutationOutcome.Applied -> {
                        database.auditDao().insert(
                            AuditLogEntity(
                                id = UUID.randomUUID().toString(),
                                at = wallClock(),
                                actorId = actorId,
                                action = action,
                                target = target,
                                result = "ALLOWED",
                                reason = null,
                                idempotencyKey = idempotencyKey,
                            ),
                        )
                    }
                    MutationOutcome.AlreadyApplied -> Unit
                }
                outcome
            }
        } catch (e: Exception) {
            writeBlockedAudit(actorId, action, target, idempotencyKey, e)
            throw e
        }
    }

    private suspend fun writeBlockedAudit(actorId: String, action: String, target: String, idempotencyKey: String, e: Exception) {
        val reason = when (e) {
            is ShortfallException -> "shortfall shortfall=${e.shortfall}"
            is RbacDeniedException -> e.reasonKey
            is InvariantViolationException -> "invariant_violation ${e.message}"
            is DuplicateValueException -> e.reasonKey
            is ReferencedByException -> e.reasonKey
            else -> "error ${e.javaClass.simpleName}"
        }
        try {
            database.withTransaction {
                database.auditDao().insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        at = wallClock(),
                        actorId = actorId,
                        action = action,
                        target = target,
                        result = "BLOCKED",
                        reason = reason,
                        idempotencyKey = idempotencyKey,
                    ),
                )
            }
        } catch (auditFailure: Exception) {
            // Auditing the refusal must never crash the refusal path; the original error propagates.
            android.util.Log.w("TransactionRunner", "failed to persist BLOCKED audit action=$action actor=$actorId", auditFailure)
        }
    }
}
