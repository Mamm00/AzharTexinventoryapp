package com.alazhartex.stock.domain

import com.alazhartex.stock.domain.model.InvariantViolationException
import com.alazhartex.stock.domain.model.ShortfallException
import com.alazhartex.stock.domain.stock.StockMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** T5 (negative-stock typing) + T6 (transfer compensation math) + T2 helpers. */
class StockMathTest {
    @Test
    fun derived_opening_is_closing_minus_deltas() {
        assertEquals(90L, StockMath.derivedOpening(closing = 100L, deltaSum = 10L))
        assertEquals(100L, StockMath.derivedOpening(closing = 100L, deltaSum = 0L))
    }

    @Test
    fun removal_more_than_available_throws_typed_shortfall_with_exact_integer() {
        val ex = assertThrows(ShortfallException::class.java) {
            StockMath.requireSufficient("mapping-1", available = 40L, requested = 175L)
        }
        assertEquals(135L, ex.shortfall)
        assertEquals(40L, ex.available)
        assertEquals(175L, ex.requested)
    }

    @Test
    fun removal_equal_to_available_is_allowed() {
        StockMath.requireSufficient("mapping-1", available = 100L, requested = 100L)
    }

    @Test
    fun closing_zero_is_allowed() {
        StockMath.requireNonNegative(0L)
    }

    @Test
    fun negative_closing_rejected() {
        assertThrows(InvariantViolationException::class.java) { StockMath.requireNonNegative(-1L) }
    }

    @Test
    fun transfer_pair_must_net_to_zero() {
        assertTrue(StockMath.transferPairNetsToZero(-25L, 25L))
        assertFalse(StockMath.transferPairNetsToZero(-25L, 24L))
    }

    @Test
    fun invariant_check_catches_cache_mismatch() {
        assertThrows(InvariantViolationException::class.java) {
            StockMath.checkInvariant("mapping-1", openingCache = 99L, closing = 100L, deltaSum = 0L)
        }
        StockMath.checkInvariant("mapping-1", openingCache = 100L, closing = 100L, deltaSum = 0L)
    }
}
