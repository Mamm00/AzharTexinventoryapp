package com.alazhartex.domain.stock

import com.alazhartex.domain.model.InvariantViolationException
import com.alazhartex.domain.model.ShortfallException

/**
 * Zero-drift invariant (spec §2 D2, §4.7, §9.7): opening + Σ deltas == closing.
 * opening is DERIVED; opening_cache is a performance cache written in the same transaction.
 */
object StockMath {
    fun derivedOpening(closing: Long, deltaSum: Long): Long = closing - deltaSum

    fun requireNonNegative(closing: Long) {
        if (closing < 0L) throw InvariantViolationException("closing_negative closing=$closing")
    }

    /** Refuses a removal larger than the available amount with the exact shortfall (T5). */
    fun requireSufficient(mappingId: String, available: Long, requested: Long) {
        if (requested > available) throw ShortfallException(mappingId, requested, available)
    }

    /** Transfer compensation validation (T6): paired deltas must net to zero across the pair. */
    fun transferPairNetsToZero(outDelta: Long, inDelta: Long): Boolean = outDelta + inDelta == 0L

    /** Full-row invariant check; throws InvariantViolationException on any drift. */
    fun checkInvariant(mappingId: String, openingCache: Long, closing: Long, deltaSum: Long) {
        val opening = derivedOpening(closing, deltaSum)
        if (opening != openingCache) {
            throw InvariantViolationException(
                "opening_cache_mismatch mapping=$mappingId cache=$openingCache derived=$opening",
            )
        }
    }
}
