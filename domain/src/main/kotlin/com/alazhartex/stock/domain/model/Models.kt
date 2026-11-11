package com.alazhartex.stock.domain.model

/** RBAC roles. SALES_MANAGER exists as a read-only label role (RES-018); no account is seeded with it. */
enum class Role { ADMIN, KEEPER, SALES_MANAGER }

enum class MovementType { IN, OUT, TRANSFER_OUT, TRANSFER_IN, ADJUSTMENT }

enum class RollState { ACTIVE, INACTIVE }

enum class Grade { A, B, C, D }

enum class AuditResult { ALLOWED, BLOCKED }

/** Typed refusal when a mutation would drive closing below zero (spec §4.5/§5 T5). */
class ShortfallException(val mappingId: String, val requested: Long, val available: Long) :
    IllegalStateException("closing_would_be_negative requested=$requested available=$available shortfall=${requested - available}") {
    val shortfall: Long get() = requested - available
}

/** Raised when a keeper attempts an action reserved to admins (spec §4.4). */
class RbacDeniedException(val role: Role, val action: String, val target: String, val reasonKey: String) :
    SecurityException("rbac_denied role=$role action=$action target=$target reason=$reasonKey")

/** Raised on unrecoverable session anomaly (backwards wall clock beyond tolerance). */
class SessionClockAnomalyException(message: String) : IllegalStateException(message)

class InvariantViolationException(message: String) : IllegalStateException(message)

/** Raised when a masters write would persist a duplicate of a unique pair (v1.1, delta contract §12). */
class DuplicateValueException(message: String, val reasonKey: String) : IllegalStateException(message)

/** Raised when a delete is refused because child rows reference the target (FK RESTRICT / app guard). */
class ReferencedByException(message: String, val reasonKey: String) : IllegalStateException(message)
