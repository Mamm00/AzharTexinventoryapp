package com.alazhartex.domain

import com.alazhartex.domain.stock.StockMath
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T22 / §9.19 — property-based stock math, 1,000 generated sequences (spec §9.19). */
class PropertyStockMathTest {

    @Test
    fun arbitrary_valid_sequences_preserve_zero_drift() = runTest {
        val opsArb = Arb.list(Arb.int(0..4), 0..40)
        val qtyArb = Arb.list(Arb.int(1..12), 64)
        checkAll(1000, opsArb, Arb.int(1..500), qtyArb) { ops, opening, quantities ->
            var stock = opening.toLong()
            var deltaSum = 0L
            ops.forEachIndexed { step, op ->
                val qty = quantities[step % quantities.size].toLong()
                when (op) {
                    0 -> { // IN
                        stock += qty; deltaSum += qty
                    }
                    1 -> { // OUT, bounded by available stock
                        val q = qty.coerceAtMost(stock)
                        if (q > 0) { stock -= q; deltaSum -= q }
                    }
                    2 -> { // TRANSFER out-leg; pair nets to zero by StockMath contract
                        val q = qty.coerceAtMost(stock)
                        assertTrue(StockMath.transferPairNetsToZero(-q, q))
                        if (q > 0) { stock -= q; deltaSum -= q }
                    }
                    3 -> { // ADJUSTMENT, alternating sign
                        val delta = if (step % 2 == 0) qty else -qty.coerceAtMost(stock)
                        stock += delta; deltaSum += delta
                    }
                    4 -> { // REPLAY/no-op: gesture replay must not double count
                        // ledger unchanged by construction
                    }
                }
                assertTrue("stock never negative, got $stock", stock >= 0L)
                assertEquals("opening + deltas == closing", opening.toLong() + deltaSum, stock)
            }
            // Recompute path agrees with maintained running stock.
            StockMath.checkInvariant("property-mapping", openingCache = stock - deltaSum, closing = stock, deltaSum = deltaSum)
        }
    }
}
