package com.alazhartex.domain.idempotency

/** Outcome contract for replay-safe gestures (spec §4.7, D7, T7). */
sealed interface MutationOutcome {
    /** The mutation was applied exactly once. */
    data class Applied(val auditId: String? = null) : MutationOutcome

    /** A previous call with the same idempotency key already took effect; no second write occurred. */
    data object AlreadyApplied : MutationOutcome
}
